# Realtime voice pipeline

[简体中文](../../zh/voice/pipeline.md) · English

> End-to-end deep dive of [Realtime voice system](realtime-voice-system.md):
> the path from mic to playback and the reasoning behind each stage. The main
> document owns the overview, styles, provider matrix and change checklist.
> This file owns the per-stage *why*, gotchas and contracts; names, constants
> and code flows are read from the source, not cached here.

## End-to-end path

### Entry and permissions
Ask for the microphone permission at entry; start only when already granted,
and hint + collapse on denial.
- **Back and hang-up are not the same.** System back and the "back to chat"
  capsule end the call *and return to text chat*; the in-page hang-up only
  ends the session and stays idle on the voice page. Conflating them is the trap.

### Capture
Use blocking reads of a device-dependent block size; derive the first-sample
timestamp from the audio clock, never the callback.
- **Framing lives in the detector, not the recorder.** It accumulates arbitrary
  input and emits a frame only when one is ready — that is why the segmenter
  can assert frame size. Never move framing into the recorder or feed it raw blocks.
- **Never timestamp from callback arrival time.** It drifts against actual
  playback and silently kills echo cancellation; take the timestamp from the
  recorded audio clock.

### Echo cancellation: hardware first, software fallback, mutually exclusive
Prefer hardware AEC when available, otherwise the software canceller. Never both.
- **Mutually exclusive, not additive.** Double cancellation damages near-end
  speech and burns CPU for nothing; confirm the two layers cannot both run
  before touching this branch.
- **Time the reference from the hardware playback clock.** The canceller is
  fed by playback but driven by the mic timeline, so reference frames must
  be clocked by the audio timestamp. Mixing packet-out time, callback time
  and the hardware clock is the most common cause of broken echo cancellation.
  Playback and AEC run at different rates with coupled frame geometry —
  change one end, change both.
- **The playback echo gate is always on during playback, regardless of AEC
  success.** AEC resets on session start and route change; while re-converging,
  residual echo looks like speech to the VAD. A short fixed window fails: it
  may expire before the first packet, and residue later in a long utterance
  still reaches the segmenter at full confidence — firing speech onset and
  feeding the assistant's own playback back to ASR. Full-duplex and cascade
  share the detector entry, so they fail together. "Always on" means the
  *decision* runs every frame, not everything is clamped: clearly louder
  near-end speech still passes, or barge-in disappears and full duplex becomes
  half duplex. Clamped frames still feed the segmenter and uplink pre-roll,
  preserving pre-roll audio, VAD state and barge-in lead-in.
- **Disarm on `player.playing`, not the playback reference level.** That level
  is clocked by playback position; when its anchor drifts it falsely reports
  silence, disarming the gate at the loudest moment. The echo floor comes from
  measured mic level, independent of the playback clock, and stays reliable.
- **`playing == false` still leaves an acoustic tail.** Reverb, speaker
  ringing and AEC residue remain after the queue drains — keep the tail
  guarded or a sudden pickup lands on the frame where the guard disarms. The
  uplink playback check must use playing state *and* tail risk too, else the
  tail goes up at full rate and server VAD opens a user turn: a second path.
- **Muting stops VAD input, not the mic.** It never stops the recording stream
  or echo processing; it stops feeding the detector, so speech onset cannot
  fire — **you cannot barge in while muted** (a second guard enforces this).
  Intent: unmuted means the mic never stops, so speaking is interruptible
  without a separate "listening window".

Trade-off: delaying barge-in on first playback and requiring confirmed frames
makes quiet barge-in sluggish and needs real-device calibration; unconditional
clamping kills the loop but degrades to half duplex.

### VAD and segmentation
- **Rebuild the input tensor every frame.** Reusing it leaves the input
  permanently zero — speech is never detected.
- **The utterance cap lives in two places with different behavior.** The
  segmenter truncates and hands the audio over; the downstream input guard
  throws. Only truncation is reachable, so a long monologue splits into turns
  and never errors. The throwing check is defensive against future code that
  bypasses the segmenter and feeds raw audio straight through.

### Uplink and recognition
Recognition input is the complete PCM of one utterance, not a stream. Vendors
differ entirely in transport — keep this table current when adding a provider:

| Vendor | Transport | Chunking | Encoding |
|---|---|---|---|
| DashScope (`qwen-audio`) | WebSocket | ~1 s | raw PCM16 |
| MiniMax (`minimax-speech`) | HTTP multipart | whole utterance | WAV |
| MiMo (`mimo-speech`) | HTTP chat-completions | whole utterance; SSE for TTS | Base64 WAV up, PCM16 down |
| OpenAI-compatible (`deepseek`/`openai`/`kimi`/`glm`) | HTTP chat-completions | whole utterance | Base64 WAV |
| OpenAI dedicated (`openai-speech`) | HTTP multipart | whole utterance | WAV |

- **DashScope `finish-task` must carry an empty input**; non-empty returns
  `InvalidParameter`.
- **Streaming models reject the intermediate-result and disfluency-removal
  flags**; sending them wastes the whole request. Streaming is picked by
  model id.
- **Empty result means no generation.** Only finals become this turn's input;
  partials only refresh the UI. A blank result returns to listening with no
  answer and no context slot — why a cough buys nothing.
- **Keep MiMo's protocol separate.** It shares chat-completions but its audio
  is a data-URI, its language option is vendor-specific, synthesis text sits
  in the assistant message and its voice table is its own — do not fold it
  into the OpenAI-compatible path. Vendors without a streaming adapter degrade
  to one-shot recognition with a single final, so callers need no branch.

### Generation
- **Fold the current question into history as the trailing user turn.** The
  response starts only when history ends with the user; sending old history
  and the question separately replays a previous unanswered question — a bug
  scar.
- **The cascaded voice path has no tools, guaranteed three ways:** the model
  asserts an empty tool list, the instruction says tools are unavailable,
  and no tool configuration is passed.
- **The prompt is two layers:** a replaceable persona plus a fixed output
  baseline appended unconditionally (TTS-friendly sentences, no markdown,
  reply in the user's language). Presets are re-read each turn, so switching
  mid-call takes effect next turn; the same preset drives cascaded and native
  realtime.
- **Assistant mode auto-approves every tool.** Confirmation-requiring tools
  are declared but answered as confirmed at runtime — voice has nowhere for
  a modal.

### Chatbot persistence
All Chatbot entries share one conversation in the chatbot partition. The
behavioral differences that matter:
- **Cascaded** reads the restored history each turn as context.
- **Native realtime** writes without reading — it never injects history into
  model context; each call and reconnect starts fresh and history lives only
  in the persisted conversation.
- **Hanging up a cascaded Chatbot call saves the active reply's generated text.**
  If playback is still in progress, chat history includes the text generated
  before hang-up. When the user interrupts a reply and stays in the call, only
  fully played sentences are saved.

### Synthesis and playback
Synthesis prefers a session path (currently one bidirectional vendor), else
one-shot per sentence.
- **Cancel the session before closing**, or the service keeps synthesizing
  and billing; the cancel is a protocol-level message, not a client disconnect.
- **Device playback, not server turn-end, updates heard text.** Only audio
  actually played out counts as heard; compare in consistent units — the head
  position counts frames, written bytes count samples, equal only for mono,
  so multi-channel needs the comparison fixed.
- **Playback is serial.** Carry partial bytes across chunks and assert no
  active track before a new segment, or segments interleave.
- **Run the playback loop off the main thread.** Main-thread state updates
  and routing callbacks contend with audio writes; the symptom is dropped
  frames in live recording.

### Barge-in
Three independent triggers, same effect (stop playback, cancel generation,
clear captions): local speech onset (dominant — cancel generation immediately,
before the next recognition result, so nothing is spent on an answer about to
be discarded); a backend interrupt event; and playback generation invalidation.
There is no manual stop control — barge-in requires speaking.

### Four typical branches
Timelines build intuition faster than prose.
**① Normal turn**

```text
speech onset -> speechStarted(): revision++ / stop playback / cancel old answer / phase=Listening
speech end   -> onUtterance -> recognize() -> phase=Transcribing; partials refresh UI only, final is input
non-empty    -> cancelAndJoin old reply -> phase=Thinking / revision++ / responseText cleared
generation   -> text deltas append; audio segments play serially
first byte to device -> phase=Speaking / speakingText=that sentence
sentence out -> spokenText += sentence / phase=Thinking
segment flow closes -> playback.join() -> phase=Listening -> finishTurn()
```
**② Empty recognition**: final is blank (cough, noise) → straight back to
listening, no generation, no context slot, no model spend.
**③ Interrupted turn**: user speaks during speaking → speech onset wins,
stops playback, cancels generation, clears the current sentence, back to
listening. Played-out sentences stay in heard context; the unplayed remainder
is dropped in Chatbot.
**④ Failure and retry**: failure at any stage stops recording, restores
routing and marks the session failed while keeping UI and session alive.
Retry rebuilds the recording stream and the whole DSP chain, then re-runs
start — it does not merely resume playback.

### Assistant voice presentation
The call controller selects the chatbot backend for every pipeline; the
retained assistant backend uses assistant instructions with a shared
voice-response contract and ignores Chatbot presets. Replies lead with the
conclusion in short natural sentences, wait after a hands-on step, announce
noticeably long work briefly and honor requests for detail; code, docs and
links go through tools with spoken summaries, or the limitation is stated.
