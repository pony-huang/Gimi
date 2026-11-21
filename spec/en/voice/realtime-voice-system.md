# Realtime voice system

[简体中文](../../zh/voice/realtime-voice-system.md) · English

> System-level contract for realtime voice: ownership, conversation style, the three pipelines, provider capabilities, known gaps, and what to verify when changing it. Per-stage mechanics — numbers, thresholds, state tables, code symbols — live in the [deep dives](#deep-dives). This document keeps only what code cannot tell you: why a choice was made, the gotchas, the cross-module contracts, and where implementation and contract disagree.
>
> Read [Known gaps](#known-gaps) before touching code: the body is design intent, the gaps are where reality differs. Look up unfamiliar terms in the [Glossary](#glossary).

## At a glance

Realtime voice defaults to **client-side VAD turn-taking, not server full-duplex**; each turn opens a fresh backend connection. The cascade path:

```text
microphone
   |  echo cancellation (hardware preferred, software fallback, mutually exclusive)
   v
VAD speech probability per frame
   |  segmentation: start/end thresholds, pre-roll, per-utterance cap (numbers: pipeline spec)
   v
whole-utterance PCM -> recognition -> final text (partials display-only)
   |
   +- Chatbot: cascaded voice model, saved history, no tools
   v
sentence-by-sentence TTS (session-based preferred, one-shot fallback)
   v
PCM playback
   ^-- user speaks -> stop playback immediately, cancel in-flight generation and synthesis
```

Three consequences:
- **Barge-in is cheap and unambiguous.** Speech cancels the whole paid chain (LLM + TTS); nothing waits for server turn-end.
- **Connections are never reused across turns.** Every turn re-handshakes and resends context; long-call latency and cost are unoptimized.
- **An utterance has a hard cap.** Long monologues force-split into multiple turns rather than erroring.

Native realtime is the deliberate exception: a persistent full-duplex session with continuous uplink, where server VAD owns turn boundaries. It reuses the capture/AEC/playback shell but bypasses the cascade pipeline. See [Conversation style](#conversation-style-and-voice-pipeline) and [Providers](#providers-and-capability-matrix).

## Layers and ownership

| Module | Owns | Does not own |
|---|---|---|
| `domain:voice` | Session contract, state snapshot, failure taxonomy, capability-resolution and session-synthesis interfaces | Any Android / ADK / Compose type |
| `core:audio` | Recording, VAD, software AEC, playback reference buffer, audio format contract | Business semantics |
| `data:voice` | Session assembly, AEC/VAD orchestration, playback, device routing, capability registry, foreground service, process-level controller | Model selection, agent execution, vendor adapters |
| `:data:modelaccess` | Vendor model/speech adapters (realtime, cascade, ASR/TTS protocols and probes), factory assembly | Devices, AEC, playback, session ownership |
| `data:agent`/`voice` | Voice session runtime, duplex session facade, realtime tool bridge; wires Chat mode to the full agent | Vendor model adapters (now in `:data:modelaccess`) |
| `feature:chat`/`realtime` | UI state mapping, forwarding user intent | Session ownership, lifecycle teardown |
| `:app` | Manifest, permissions, notification-permission guidance, navigation return | — |

Ownership rules:
- The **process-level call controller** owns the session; leaving the page, backgrounding, or destroying the Chat entry must never close it.
- Release resources only when the user explicitly ends the call — from the voice page or the status-bar notification.
- The UI entry point obtains the recording permission; the session layer only classifies a missing microphone as a `Microphone` failure.

## Deep dives

Detail lives in sibling files (Chinese companions under `spec/zh/voice/`); this document keeps design intent, contracts, and the checklist:
- [Realtime voice pipeline](pipeline.md) — end-to-end path, numbers, thresholds, the four typical branches.
- [Realtime voice state, devices, lifecycle](runtime.md) — state machine, generation counters, device routing, background survival, reliability.
- [Realtime voice test coverage](testing.md) — what each test guards, what only hardware verifies.

## Conversation style and voice pipeline

All three pipelines use Chatbot. Text mode and voice bind the same Chatbot conversation; starting or switching a call accepts only Chatbot.

| Pipeline | Model roles | Availability |
|---|---|---|
| Cascaded | Recognition → Chatbot conversation model → synthesis | Valid recognition and synthesis models |
| Semi-cascaded | Chatbot model understands audio directly; a synthesis model speaks the reply | Omni-capable Chatbot model and valid synthesis model |
| Native realtime | A vendor full-duplex Realtime model handles audio both ways | Valid Realtime model with a registered duplex adapter |

Cascaded and semi-cascaded restore the bound conversation and persist user input and fully played replies. Native realtime saves only finalized transcripts and starts without replaying history.
**Configuration inheritance.** Global defaults supply every field; a conversation may override fields individually and uncovered fields inherit. Keep the saved configuration intact while a pipeline's required models are still missing, then commit the complete set atomically. Abandoning an incomplete edit discards only the incomplete selections.

### Chatbot roles and navigation

Roles come from a shared catalog; text and voice read the same per-conversation selection. A role change preserves history and affects subsequent replies. Native realtime pushes instruction updates onto the existing connection without touching audio, voice, or tools; text and cascaded paths apply the role through their normal instruction/turn flow. Start realtime chat from the microphone-adjacent entry in the Chatbot composer; text chat keeps its own dictation entry. Requesting a new conversation while the current one is already empty and idle shows a notice and keeps that conversation.

### Realtime assistant tools

The backend retains a client-executed tool bridge; the app's call controller opens only Chatbot calls, which have no assistant tools. These rules govern the retained path:
- **Allowlist, not auto-discovery.** The bridge reuses the chat agent's contribution sources but runs only an explicit allowlist (local, mcp, plugin, appfunction, sandbox). Excluded on purpose: sources needing the ADK runner (session state, artifacts, user-input suspension, skills), vendor-declared / remote-executed sources that throw on local run, and catalog tools meaningless mid-call. A new source joins only through a conscious allowlist decision.
- **No runner, no persisted session.** Execution runs on a bare context; voice calls auto-approve every confirmation, same policy as voice Chat mode.
- **Declaration is per-call and survives reconnect.** A broken MCP server can add its probe budget to call start; reconnect is a new server session, so declarations are re-sent automatically (wire shapes: pipeline spec).
- **Round flow.** Buffer a tool call as arguments stream in, finalize on completion, flush unfinished calls when the response completes. Discard truncated arguments on explicit non-completion statuses and on barge-in — never execute or write them back. Rounds are serialized off the receive loop; results are written back and one response triggered per round. Arguments are never spoken; disconnect cancels in-flight rounds.
- **UI feedback.** A pending tool round keeps the call in its thinking state so the second round's transcript keeps appending; the "calling tool" notice clears on the second round's first output, on interrupt, on reconnect, or on the next user turn.

### Default Realtime selection

The Realtime model is set globally or overridden per conversation while idle. Keep these gotchas:

| Fact | Rule |
|---|---|
| Candidate scope | Only enabled, keyed services that declare a native Realtime adapter contribute candidates; a Realtime-marked model without an adapter would silently degrade to cascade, so it is **hidden, not shown-disabled**. |
| Voice table | **No voice table is built.** Each adapter declares one default voice per model and whether a free-text override is accepted (one vendor takes a cloned voice id, the other a fixed dropdown). |
| Voice binding | A conversation voice choice saves to that conversation; clearing it falls back to a remembered voice only for the same pipeline and full model, else to the vendor default. |
| Availability | Deleting the service, key, model, or adapter makes the selection count as missing. |
| Duration | The configuration snapshot is **fixed at call start**; settings reopen only after the call fully ends. |

### Configuration switching

Configuration is locked for the lifetime of a call. The one gotcha worth knowing: if a switch fails after the current turn is flushed, **the original session is kept** so the pending turn can be retried — never tear it down on a failed switch.

## Providers and capability matrix

**STT and ASR are two capability slots, not two names for one thing.** STT is a dedicated whole-utterance transcription service; ASR reuses a chat model for recognition. When a model has both, dedicated STT wins (more accurate, shorter path).

| Service | Recognition | Streaming | Synthesis | Session synthesis | Voices | Probe |
|---|---|---|---|---|---|---|
| qwen-audio | Dedicated STT | Yes | Yes | No | Per-model table / default voice + free-text override | Realtime WS handshake |
| minimax-speech | STT (multipart) | No | SSE | Bidirectional WS | Remote + local fallback | None registered |
| mimo-speech | STT + ASR | No | Yes | No | Static table | Models endpoint |
| openai-speech | Dedicated STT | No | Yes (binary PCM) | No | Static table / built-in voices, no custom | Realtime WS handshake |
| chat services | STT + ASR | No | Yes | No | Empty | Chat service probe |

DashScope cascade has no session-based synthesis for a concrete reason: the vendor bills per connection, so reusing a long-lived connection buys nothing. **Real and cascade models share one configuration per vendor** — there is no separate Realtime service kind; models live in their vendor's speech service, marked so cascade pickers never select them. Sessions stay persistent full-duplex: server VAD owns turn boundaries, the model hears and speaks audio natively. Non-obvious facts of the native path (the seam itself is code-visible):
- **Uplink rate is per-vendor.** One resamples to the model's native rate, the other feeds capture rate directly. Follow the adapter's declared uplink contract; never hardcode a rate in the factory.
- **Barge-in semantics are per-vendor.** One relies on server auto-cancel plus a truncation supplement; the other relies purely on server auto-cancel and drops residual audio in a silent window. Send no truncate/cancel events to a vendor that auto-cancels.
- **Playback uses hybrid gating** — local VAD gates uplink during assistant speech so residual echo cannot fake a server-side barge-in. This is why capture keeps its own VAD even in full-duplex mode.
- **Handshake-failure classification is injected**, because the transport kernel knows only HTTP status, not domain meaning.

### Capability resolution

| Rule | Behavior |
|---|---|
| Match key | Synthesis matches on service id alone; recognition requires both service id and capability. |
| Missing streaming entry | Returns null → caller degrades to one-shot recognition. Soft failure, not exception. |
| Language-model capability | The only always-true entry; this layer does not judge model availability. Every other declared-but-unregistered pair fails explicitly — don't infer leniency for STT/TTS/ASR from it. |

### Failure classification

Failures form a **translatable** taxonomy — the UI shows mapped copy, never a raw exception.

| Category | Origin |
|---|---|
| Configuration | Missing defaults, failed availability assertion |
| Audio processing | VAD / AEC construction |
| Microphone | Audio focus denied, recorder start failure, recording callback error |
| Recognition | Recognition flow error while the revision is current |
| Generation | Generation flow error with no more specific category |
| Synthesis | One-shot TTS failure that is not a cancellation |
| Playback | Playback failure |
| Persistence | Target conversation gone on entering Chat mode; close failing during submission. Playback receipts are no-ops, so a "save failed" main path does not exist. |
| Conversation busy | Agent runtime gate rejected a concurrent session |

A separate **non-mapped, lower-level** taxonomy exists for settings screens and connectivity tests; the call UI cannot see it. Keep the two apart.

## Manual input during calls

During a call, chat and mute stay available and manual text/attachments submit through the normal paths; preparation finishes before playback is interrupted and failed submissions keep their draft for retry. OpenAI native realtime accepts text and images as one user item with an explicit response trigger; other native vendors lack a manual-message sink and reject documents and audio explicitly, with manual messages never buffered across reconnects.

## Known gaps

The following is the actual state of the code, not a to-do list. Confirm each is still true before relying on it — or before assuming it was fixed.
1. **The retained assistant backend has no-op playback receipts.** Persistence relies on the runner, so playback/finish/retry receipts do nothing there. Simple mode saves user input before generation, fully played sentences at turn completion, and generated text from the active reply on hang-up. It restores the current conversation and holds a runtime lease, using stable event IDs across retries. Native realtime Chatbot saves finalized transcripts to its bound conversation.
2. **The retained assistant backend persists complete replies.** The runner stores complete events, so an interrupted answer reappears verbatim when chat reopens. When the user interrupts a reply and continues a Simple mode call, only fully played sentences enter context.
3. **The default realtime repository has no unit tests.** The playback-echo decision itself is extracted and tested, but the wiring inside the repository (playing / tail-risk checks, reference-level plumbing, uplink channel race) has no test seam; the recorder, software canceller, JNI layer, and audio track are hardware-only.
4. **Only two native live providers exist.** Vendor-owned endpoints are live for both; the native seam is real but only the cascade adapter and the two Realtime adapters implement it. A text model implementing the interface still does not automatically gain realtime voice capability.
5. **MiniMax has no registered connectivity probe**, so its settings "test connection" behavior is unverified.
6. **No dedicated stop-playback control.** Speaking or submitting an accepted manual message interrupts the current answer.
7. **Simple mode rebuilds the connection and resends many turns of context every turn**, so long-call latency and cost are unoptimized.
8. **Realtime requires a valid model selection.** Each conversation inherits the global selection or specifies its own while idle; the voice choice saves to that conversation.
9. **Tool rounds race barge-in by design.** If the user speaks while a tool executes, the round still writes results and triggers a response, so the server may speak mid-barge-in. The repository stops playback immediately (local VAD) and clears the tool notice, so the worst case is a late answer, not a stuck state. Only device verification covers this.

## Checklist when changing this system

- **Adding a provider** — miss any item and something breaks in settings or at runtime: implement and register recognition/synthesis adapters with service ids and capabilities; register each `(service id, capability)` pair (streaming needs an explicit entry or it silently degrades to one-shot); register the voice table (cache key includes model id) with a local fallback; implement a connectivity probe or "test connection" is undefined (MiniMax today); keep endpoints and auth in the data layer (vendor-owned constants fixed; only proxy services take a user base URL); route all selection through the one model factory, unknown vendors fall back to cascade; add contract tests on a real dispatcher, not virtual time.
- **Adding a native realtime provider** (full-duplex, on top of the above): open one persistent session and gate uplink on config ack — never stream before confirmation; add a realtime model group under the vendor's speech service (realtime marker, no cascade markers) plus a handshake probe, with vendor-owned endpoints; **declare the adapter's capabilities** (service id, uplink contract, default voice per model, built-in voices, custom-voice policy) or the models never appear as candidates; route through the duplex backend (facade owns reconnect backoff and reconnect-as-new-session, repository only consumes domain events and feeds frames); respect uplink gating — continuous when idle, locally VAD-gated during playback (see pipeline spec for pre-roll/threshold); declare the per-vendor uplink contract in the adapter, not the factory (see pipeline spec for rates and wire shapes); match the vendor's barge-in semantics (no truncate/cancel events to auto-cancel vendors; supplement only where needed).
- **Changing the tool surface** — edit only the source allowlist; anything needing the runner (session state, artifacts, user-input suspension, skills) stays out, and a new source joins only by conscious decision. Wire shapes and execution-semantics tests: see pipeline / testing spec.
- **Changing VAD thresholds** — the VAD test will catch you; confirm the per-utterance cap still holds or a long monologue grows memory unbounded.
- **Changing playback format** — output stays 24 kHz mono PCM16; convert inside the adapter or change both player and AEC reference geometry (see pipeline spec).
- **Changing routing, AEC convergence, or the playback gate** — the converge window starts at the next playback start; later sentences in the same reply don't reopen it. During playback and the acoustic tail, clamp echo-dominated frames to zero VAD probability using a measured echo floor plus the playback reference level. The disarm switch must be the player's playing state (reference level can falsely drop to zero), and the tail after playback must not disarm immediately. The uplink gate must check both playing state and tail-risk, never the presence of a duplex session. Run the echo-guard test first (minimal controls: playback starts inside the window, reverb tail after playback, reference-level dropout).
- **Changing session state** — every new async result needs a revision guard, or a late result after cancellation leaks into the next turn.
- **Changing domain interfaces or bindings** — assemble and install a full APK, then restart the process preserving user data; assembly without install, or a hot fix on an older APK, can mix incompatible runtime types.
- **Verifying device behavior** — authentication, playback, and routing need real hardware; interface conformance does not prove platform support.

## Glossary

| Term | Meaning |
|---|---|
| ADK | Google's agent development kit for Kotlin; the inference side of realtime voice runs on its model/connection interfaces |
| AEC | Acoustic echo cancellation |
| VAD | Voice activity detection — the only source of turn boundaries here |
| DSP | The per-frame chain: AEC, VAD, segmentation |
| PCM16 | 16-bit signed samples; all audio here is little-endian mono |
| Silero | Open-source VAD model used here |
| speexdsp | Library providing the software AEC implementation |
| ONNX | Model interchange format, run by ONNX Runtime |
| SSE | Server-sent events; a server-pushed event stream |
| Turn generation | One Q&A is one generation; async results must match the current generation to apply |
| Playback generation | Incremented on stop; used to discard old-turn audio |
| Lease | Runtime exclusive access to one chat; only one writer per conversation |
| Foreground service | Android service whose microphone type permits background recording |
| Audio focus | Android audio exclusivity; here it is requested but loss is not observed |
| Communication device | The call routing device set exposed by the audio manager |
