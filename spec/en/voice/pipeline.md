# Realtime voice pipeline

[简体中文](../../zh/voice/pipeline.md) · English

> End-to-end deep dive of [Realtime voice system](realtime-voice-system.md):
> the path from microphone to playback — entry and permissions, capture, echo
> cancellation, VAD and segmentation, uplink and recognition, generation,
> synthesis and playback, barge-in, and the four typical branches. The main
> document owns the system overview, the three modes, the provider matrix, and
> the change checklist; this file owns the per-stage mechanics.

## End-to-end path

### Entry and permissions

The voice button in `ChatRoute` → `RealtimeVoiceHost` (320 ms visibility
animation) → `RealtimeVoiceRoute` requests `RECORD_AUDIO` →
`RealtimeVoiceViewModel.start()`.

- The session starts only when the permission is already granted; denial shows
  a hint and collapses the page.
- Back / system back is equivalent to the left "back to chat" capsule: it ends
  the call and returns to text chat (`RealtimeVoiceRoute` registers
  `BackHandler { onDismiss(); viewModel.end() }`). The in-page hang-up button is
  different: it only ends the session and stays on the voice page in the idle
  state, without returning to the chat list.

### Capture

`core:audio/VoiceAudioRecorder`:

| Parameter | Value |
|---|---|
| Sample rate | 16,000 Hz (`VoiceAudioFormat.INPUT_SAMPLE_RATE_HZ`) |
| Channels / depth | Mono / PCM16 little-endian |
| Audio source | `VOICE_COMMUNICATION` (for calls) / `VOICE_RECOGNITION` |
| Read mode | `READ_BLOCKING`, reading a full `minBufferSize` (block size is device-dependent, **not a 32 ms frame**) |
| Timestamps | `getTimestamp(TIMEBASE_MONOTONIC)` to derive the first-sample instant |
| Level | RMS → dBFS, mapped to 0..1 between −60 dBFS and 0; smoothing 0.65, dispatched every 50 ms |

The variable block size deserves a note: the byte count handed to the recording
callback varies per device, and **framing does not happen here**.
`SileroVoiceActivityDetector.accept()` accumulates input of any length itself and
only runs inference — emitting `onFrame` — once it has 1024 bytes (512 samples /
32 ms). That is why `VoiceActivitySegmenter` can safely `require`
`pcm16.size == FRAME_BYTES`: frame integrity is guaranteed by the detector, not
by the recorder. The detector also keeps a partial frame in `frameOffset` across
blocks and clears it on `reset()`.

Timestamps are the price paid for correctness: a code comment states explicitly
that **callback arrival time must not be used**, otherwise the AEC reference
drifts against actual playback and echo cancellation simply stops working.

### Echo cancellation: hardware first, software fallback, mutually exclusive

The branch in `DefaultRealtimeVoiceRepository.start()` is the crux:

```kotlin
if (recorder.echoCancellationEnabled) acceptCleanAudio(bytes)
else echoCanceller?.accept(bytes, firstTime, player::echoReference, ::acceptCleanAudio)
```

- **Hardware path**: `AcousticEchoCanceler` / `NoiseSuppressor` bound to this
  `AudioRecord`'s `audioSessionId`. This is an optional device capability — on
  headsets, or when a vendor implementation does not support it, the call simply
  continues.
- **Software path**: `SoftwareEchoCanceller` + `speexdsp` (`echo_jni.c`). Frames
  of 160 samples (10 ms @16 kHz), reference advanced by 20 ms, adaptive filter
  tail of 4800 samples (≈300 ms).
- **Mutually exclusive, not additive.** When hardware is available the software
  AEC does not run. The comment does not justify this; the engineering rationale
  is that double cancellation damages near-end speech and burns CPU for nothing.
  Confirm the two layers cannot both be enabled before changing this.
- **The reference signal** comes from playback and is driven by **microphone
  time**: for every processed frame (16 kHz / 160 samples) the capture thread
  calls `player.echoReference(firstSampleTimestampOfThatFrame)` and receives 240
  samples (24 kHz) of reference. Internally one `PcmPlaybackReference` (2-second
  ring buffer) is kept **per segment**, `references` retains only the last 2, and
  each is `mixInto()`-ed onto the same output using the playback clock — so the
  tail of the previous sentence is still cancelled after the next one starts.
- The reference is timed against the `AudioTimestamp` **hardware playback
  clock** — not the TTS packet-out time, and not callback arrival time. Mixing
  these three is the most common cause of broken echo cancellation.
- Playback runs at 24 kHz while the AEC reference runs at 16 kHz; the frame
  geometry aligns as `240 @24k ↔ 160 @16k`. Changing either rate means changing
  both ends.

**Playback echo gate** (`PlaybackEchoGuard`). AEC state resets in two places:
when the session starts (a fresh `SoftwareEchoCanceller`, filter converging from
zero) and on **route changes** (`echoCanceller.reset()` + `detector.reset()` +
`segmenter.reset()`). While it re-converges, residual echo looks exactly like
near-end speech to the VAD.

Opening an 800 ms window at those two moments is **not enough** to stop the
feedback loop. A window started at session launch expires before the first TTS
packet arrives (usually a second later), and even when it fully covers the start of
playback, any residual echo later in a long utterance — loudspeaker coupling,
reference-clock jitter, or a hardware AEC that reports available but does not
actually cancel this `AudioTrack` — reaches `VoiceActivitySegmenter` at full VAD
probability. That fires `speechStarted()` and, after 704 ms of silence, hands the
whole echo segment to ASR as "what the user just said". The assistant then answers
its own playback, and the cycle repeats. Both modes share the
`acceptCleanAudio()` → `detector.accept()` entry point, so full-duplex Realtime
and ASR+LLM+TTS cascade fail together.

The gate is therefore **always on during playback and independent of whether AEC
actually worked**. It acts on the detector's per-frame callback. "Always on" means
the **decision** is always on, not that everything is clamped: every frame is
judged, but clearly louder near-end speech is still released — otherwise barge-in
during playback disappears entirely and full duplex degrades to half duplex.

| Condition | Behaviour |
|---|---|
| Truly idle (playback ended more than 400 ms ago, no new playback) | Pass through, zero latency |
| 800 ms converge window before the **first playback** after an AEC reset | Clamp to 0 |
| Playing, or within the 400 ms acoustic tail after playback, without 3 consecutive frames of "VAD >= 0.8 + mic level >= 0.65 x reference + mic level above echo floor by 0.12" | Clamp to 0 |
| Those frames confirmed | Pass through and stay open until end of sentence (barge-in) |

Clamped frames are **still fed to the segmenter and the uplink pre-roll**; only the
"user started speaking" event is suppressed. Pre-roll audio, VAD hidden state and
barge-in lead-in are preserved. The echo floor is a fast-fall / slow-rise envelope
of the microphone level during playback, which adapts to device gain instead of
comparing a digital reference level against an acoustically attenuated signal.

Two easy-to-get-wrong details:

- **The disarm switch must be `player.playing`, not the playback reference level.**
  That level is clocked by `getTimestamp` / `playbackHeadPosition`; when the anchor
  drifts, `echoReference()` falsely returns silence, and using it as the switch
  disarms the gate at the loudest moment. The echo floor comes from the measured
  microphone level, independent of the playback clock, so it is the reliable
  baseline; in a silent gap the floor is low too, so a real user still passes.
- **`playing == false` does not mean the echo is gone.** `RealtimePcmPlayer`
  clears `activeTrack` as soon as the hardware queue drains, but room reverb,
  speaker ringing and AEC residue remain. The 400 ms tail must keep passing through
  the gate, or "suddenly picking up sound mid-playback" lands exactly on the frame
  where the guard disarms.

`onAecReset()` (session launch, route change) marks the window, but the clock
starts at the **next playback start**, not at the reset moment. Later sentences in
the same reply do not reopen it — the AEC instance was never rebuilt — and short
gaps keep the echo floor so per-sentence playback does not rebuild the baseline
and mistake a loud echo for near-end speech. `PlaybackEchoGuardTest` wires the gate
to the real segmenter and guards "no new user turn during playback", "reverb tail
after playback does not open a new turn", "a reference-level dropout does not
disarm the gate", and "silent gaps and clearly louder near-end speech still barge
in".

**The uplink gate's playback check** (Realtime mode) must use both `player.playing`
and `echoGuard.tailRisk()`: `finishDuplexPlayback()` clears `duplexPlayback`
*before* `join()`ing the player while it drains the tail, so judging playback by
channel presence sends that tail at full rate; and restoring full-rate uplink once
`playing` goes false lets the 400 ms reverb reach server_vad as a user turn too.
That is a second feedback path, independent of the local segmenter.

The cost: no barge-in for the first 0.8 s of the session's first playback (or the
first one after a device switch), barge-in needs 3 confirmed frames (~96 ms) during
playback and the 400 ms tail, and on loudspeaker output only near-end speech
clearly louder than the echo floor interrupts (quiet barge-in gets sluggish). The
near-end thresholds and the 400 ms tail are empirical and need real-device
calibration across loudspeaker, headset, gain and room reverb. That is still
preferable to the assistant interrupting itself and answering on a loop; if the
product would rather give up barge-in entirely, clamping everything to zero during
playback removes the loop completely but degrades to half duplex.

Muted semantics deserve their own note, because it is very easy to get backwards:

- Muting does **not** stop the recording stream and does **not** stop echo
  processing (software AEC keeps converging; the hardware path keeps querying the
  playback reference).
- Muting **does** stop feeding the VAD (`if (!muted) detector?.accept(...)`), so
  `speechStarted()` never fires — **while muted you cannot barge in by
  speaking**.
- `speechStarted()` additionally early-returns on `state.value.muted` as a
  second guard.
- So "barge-in works during playback" holds **only while unmuted and when the
  frame passes the echo gate**. The actual design intent: when unmuted the
  microphone never stops, which is why `Speaking` remains interruptible without a
  separate "listening window".

### VAD and segmentation

Silero VAD **v6.2** ONNX (`assets/silero/silero_vad_v6_2.onnx`), 512 input
samples + 64 context samples, hidden state 256, 16 kHz. One trap is easy to fall
into: **the input tensor must be rebuilt every frame**. Reusing it makes the
input permanently zero, which manifests as "speech is never detected".

Thresholds in `VoiceActivitySegmenter`:

| Constant | Value | Meaning |
|---|---|---|
| `FRAME_BYTES` | 1024 (512 samples / 32 ms) | Frame size; a mismatched length fails the `require` |
| `START_THRESHOLD` | 0.6 | Speech probability gate |
| `START_FRAMES` | 5 (≈160 ms) | Consecutive hits before declaring speech onset; suppresses transients |
| `END_THRESHOLD` | 0.35 | Silence gate (below the start gate, to avoid chatter) |
| `END_FRAMES` | 22 (≈704 ms) | Consecutive silent frames before closing an utterance; tolerates mid-sentence pauses |
| `PRE_ROLL_FRAMES` | 12 (≈384 ms) | Pre-roll so the onset is not clipped |
| `MAX_UTTERANCE_BYTES` | 16,000×2×30 | 30-second cap per utterance |

30 seconds appears twice, but with **completely different behaviour** — do not
conflate them:

| Location | Behaviour | Actually reachable? |
|---|---|---|
| `VoiceActivitySegmenter.MAX_UTTERANCE_BYTES` | **Truncates**: hands over the accumulated audio as one utterance | Yes; this is the normal path for a long monologue |
| `CascadedVoiceModel.MAX_INPUT_BYTES` | `require` fails, throws `IllegalArgumentException` | No; the segmenter already truncated |

So a long monologue manifests as **being split into multiple turns**, never as an
error. The second check is purely defensive, guarding against future code that
bypasses the segmenter and feeds raw audio straight through.

Both `accept()` and `reset()` must be `@Synchronized`: ending a call, muting, and
route changes all reset from other threads, and without the lock audio from the
previous session gets spliced into the new one.

### Uplink and recognition

The recognition request body is **the complete PCM of one utterance**, not a
continuous stream. The three vendors differ entirely in transport:

| Vendor | Transport | Chunking | Audio encoding |
|---|---|---|---|
| DashScope (`qwen-audio`) | WebSocket | 32 KB per chunk (≈1 s) | Raw PCM16 (`format=pcm`) |
| MiniMax (`minimax-speech`) | HTTP multipart | Whole utterance once | WAV (44-byte RIFF header) |
| MiMo (`mimo-speech`) | HTTP `/chat/completions` (dedicated adapter) | Whole utterance once (ASR); SSE stream (TTS) | Data-URI Base64(WAV) up; base64 PCM16 chunks down |
| OpenAI-compatible (`deepseek` / `openai` / `kimi` / `glm`) | HTTP `/chat/completions` | Whole utterance once | Base64(WAV) |
| OpenAI dedicated (`openai-speech`) | HTTP multipart | Whole utterance once | WAV (44-byte RIFF header) |

MiMo shares the chat-completions transport with the OpenAI-compatible set but
keeps a dedicated protocol implementation (`MiMoSpeechProtocol`): its audio is a
data-URI (`data:audio/wav;base64,…`), the language rides the MiMo-specific
`asr_options`, synthesis text must sit in the `assistant` message, and its voice
table (`MiMoTtsVoices`) is its own. Do not fold it back into
`OpenAiCompatibleChatProtocol` — the request shapes are genuinely different.

DashScope's sequence is `run-task` → `task-started` → binary audio →
`finish-task` → `result-generated` → `task-finished`. **`finish-task` must carry
an empty `input`**, otherwise the service returns `InvalidParameter`.

Streaming versus non-streaming is decided by the model ID
(`endsWith("-streaming")`). Streaming models **reject**
`intermediate_result_enabled` / `disfluency_removal_enabled`; sending them wastes
the whole request.

Results are aggregated by `sentence_id`, overwriting in place (the same sentence
yields a partial and then a final). A `Partial` only updates the UI; only `Final`
becomes this turn's input. An empty result returns to `Listening` and **does not
trigger generation** — that is why a cough does not buy you an answer.

Vendors without a streaming adapter degrade via
`DefaultSpeechRecognitionRepository.transcribeStreaming()` into one-shot
recognition that emits a single `Final`, so callers need no branch.

### Generation

**Simple mode** (`data:agent/voice/AdkVoiceAudioBackend.SimpleSession`):

1. On turn teardown, "this turn's user text + the previous turn's actually
   played-out answer text" is pushed as **one pair** into `heardHistory`; when
   `size > 32` the oldest is `removeFirst()`-ed. The cap is therefore **32 pairs
   = 32 turns**; an interrupted turn still occupies a slot (its "heard text" may
   be empty or only the first half).
2. Each turn calls `models.create(selection)` → `CascadedVoiceModel`, then
   `connect()`.
3. Instruction = the preset persona (re-read each turn, so switching presets
   takes effect next turn) + a fixed output baseline.
4. `sendHistory()` **folds the current question into history as the trailing
   USER turn**.
5. No tools, guaranteed three ways: `CascadedVoiceModel.connect()` asserts
   `tools` is empty; the instruction states tools are unavailable; no
   `toolConfiguration` is passed.

Item 4 is a scar from a real bug: the official interface starts a response only
when history ends in USER. Sending old history first and the current question
separately replays the previous turn's unanswered question whenever no prior
answer was ever heard.

**Prompt presets.** The instruction handed to the voice model is always two
layers: a **replaceable persona layer** and a **fixed output baseline** that is
appended unconditionally (TTS-friendly sentence rules, no markdown, and the
"reply in the user's language" lock).

| Layer | Source | Replaceable |
|---|---|---|
| Persona | The selected preset, or the built-in `VOICE_INSTRUCTION` when nothing is selected | Yes — a preset replaces it entirely |
| Output baseline | `VOICE_OUTPUT_RULES` in `AdkVoiceAudioBackend` | No |

- Presets come from two sources that share one id space: the **official
  catalog** (`domain:voice`'s `BuiltInVoicePresets`, read-only, shipped with the
  app) and **user presets** persisted in app-private preferences. Selection
  resolves official first, then user; an id that exists in neither is
  normalized to "no preset".
- The official prompts are authored as complete realtime system prompts: the
  section skeleton (Role and Objective, Personality and Tone, Language,
  Reasoning, Message Channels, Preambles, Verbosity, Tools, Unclear Audio,
  Entity Capture, Long Context Behavior, Escalation) that realtime models are
  steered with, including the safety escalation rules that stop the persona
  when the user is in real distress.
- The same preset drives **Simple mode** and the **Realtime duplex mode**
  (`openDuplex()`); Simple re-reads it per turn, so switching mid-call takes
  effect on the next turn.

**Chat mode** (`VoiceAgentConversation`): each turn hands the recognized text to
the `AgentChatRunner` shared with text chat, and streams body deltas into TTS
sentence by sentence.

- **Every tool is auto-approved.** Confirmation-requiring tools are still
  declared; at runtime each `callId` is answered with
  `respondToToolConfirmation(confirmed = true)`. Voice has nowhere to put a
  modal.
- **User-input tools** (`get_user_choice` / `adk_request_input`) are not
  auto-answered. Instead `pendingInput` is recorded, the turn ends, and the
  original call resumes when the user answers out loud next turn.
- **Context and persistence are not shuttled by this layer.** Events are written
  to the Session directly by the ADK Runner; this layer only notifies content
  changes at the end of each turn and on `release()`, so the chat UI refreshes
  immediately.

### Synthesis and playback

Synthesis prefers the **session-based** path (`openSession()` non-null; currently
only MiniMax bidirectional), otherwise degrades to one-shot per sentence.

- Session-based teardown is the critical part: on interruption, `open.cancel()`
  must run before `close()`, otherwise **the service keeps synthesizing and
  billing**. MiniMax's `cancel` sends a protocol-level `task_cancel`, which is
  materially different from "client disconnects and the server keeps charging".
- Sentence buffer `VoiceSentenceBuffer`: 100 characters max per sentence,
  delimiters `。！？!?；;`, newline, and `.` followed by whitespace; surrogate
  pairs handled correctly.
- Playback `RealtimePcmPlayer`: 24 kHz mono PCM16, `USAGE_VOICE_COMMUNICATION` +
  `CONTENT_TYPE_SPEECH`, `MODE_STREAM`, `WRITE_NON_BLOCKING`.

Three details determine playback correctness:

| Practice | Why it must be this way |
|---|---|
| Non-blocking write with `delay(5)` on `count == 0` | Stopping must not depend on the native `write` returning, so cancellation cannot keep feeding stale audio |
| `trailingByte` carried across chunks | Network chunks do not align to sample boundaries; only `check(trailingByte == null)` at the end justifies claiming the audio is complete |
| `check(activeTrack == null)` hard constraint | Playback must be serial, otherwise two segments interleave |

Drain detection polls `playbackHeadPosition >= writtenBytes / 2` every 10 ms.
**Played-out on the device — not a server turn completion — is what updates the
"heard text".** Mind the units: `playbackHeadPosition` counts **frames** while
`writtenBytes / 2` counts **samples**; they are only equal for mono, so switching
to multi-channel requires fixing this comparison too.

The playback loop deliberately runs on `Dispatchers.Default` rather than the main
thread: state updates and routing callbacks on the main thread contend with the
AudioTrack writes, and the visible symptom is dropped frames in live recording.

### Barge-in

Three independent triggers with identical effect (stop playback + cancel
generation + clear captions):

1. **Local VAD detects speech onset** (`speechStarted()`) — the dominant path.
   `replyJob?.cancel()` runs the moment speech starts, without waiting for the
   next recognition result, so money is not spent on an answer that is about to
   be discarded.
2. **Backend emits `VoiceAudioEvent.Interrupted`** (server-side interrupt
   decision).
3. **Playback generation invalidated** — `RealtimePcmPlayer.checkGeneration()`
   throws `CancellationException("Voice playback interrupted")`.

There is currently **no** UI control to stop playback manually; barge-in requires
speaking.

### Four typical branches

Stringing the path into timelines builds intuition faster than reading it
piecemeal.

**① A normal turn**

```text
speech onset (5 frames >= 0.6) -> speechStarted(): inputRevision++ / stop playback / cancel old answer / phase=Listening
speech end (22 frames < 0.35)  -> onUtterance(whole PCM) -> recognize()
                               -> phase=Transcribing; Partial refreshes UI only, Final is the input
non-empty text                 -> wait for the old replyJob via cancelAndJoin
                               -> phase=Thinking / revision+1 / responseText cleared
generation + synthesis         -> text deltas append to responseText; audio segments play serially through Channel(1)
first byte reaches AudioTrack  -> phase=Speaking / speakingText=that sentence
sentence played out            -> spokenText += sentence / phase=Thinking
segment flow closes            -> playback.join() -> phase=Listening -> finishTurn()
```

**② Empty recognition**: `Final` is blank (cough, ambient noise) → straight back
to `Listening`. **No generation, no context slot, no model spend.** This is the
guarantee that a false trigger does not buy an answer.

**③ Interrupted turn**: the user speaks during `Speaking` → `speechStarted()`
wins, stopping playback + cancelling `replyJob` + clearing `speakingText`, back to
`Listening`. Sentences already played out remain in `spokenText` and become the
next turn's heard context; in Simple mode the unplayed remainder is dropped
outright.

**④ Failure and retry**: `fail()` at any stage → stop recording, restore routing,
`phase=Failed` (session and UI both survive). `retry()` rebuilds the recording
stream and the entire DSP chain and runs `start()` again rather than merely
resuming playback.
