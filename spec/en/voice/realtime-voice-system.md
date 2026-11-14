# Realtime voice system

[简体中文](../../zh/voice/realtime-voice-system.md) · English

> This is the system-level document for realtime voice: ownership layers, the
> three modes, multi-provider adaptation, known gaps, and the change checklist.
> Per-stage mechanics live in the sibling [deep dives](#deep-dives)
> (end-to-end pipeline, state/lifecycle, test coverage). The older
> `spec/en/realtime-voice-models.md` (which covered just the model adapter layer)
> went stale and has been removed — see git history.
>
> Reading order: look up unfamiliar terms in the [Glossary](#glossary), and
> **read [Known gaps](#known-gaps) before touching code** — the body describes
> design intent; the gaps section records where the implementation and the
> contracts disagree.

## At a glance

Realtime voice is **not** a server-side full-duplex conversation by default. The
cascade modes are **local VAD segmentation plus one fresh backend connection per
turn**. The whole path looks like this:

```text
microphone, 16 kHz mono
   |  echo cancellation (hardware AEC preferred, software AEC fallback, mutually exclusive)
   v
VAD emits a speech probability per 32 ms frame
   |  segmentation: start 0.60 for 5 frames / end 0.35 for 22 frames / 12-frame pre-roll / <=30 s per utterance
   v
whole-utterance PCM -> recognition -> final text (partials are display-only)
   |
   +- Simple: cascaded voice model, 32 in-memory pairs, no tools
   +- Chat  : full chat agent, every tool confirmation auto-approved, text written by the ADK Runner
   |
   v
sentence-by-sentence TTS (session-based preferred, otherwise one-shot per sentence)
   v
24 kHz mono PCM16 -> AudioTrack playback
   ^
   +--- user starts speaking -> stop playback immediately, cancel in-flight generation and synthesis
```

Three consequences you must keep in mind:

- **Barge-in is cheap and unambiguous.** The moment speech starts, the entire
  paid chain (LLM + TTS) is cancelled. Nothing waits for server-side turn-end
  detection.
- **Connections are never reused across turns.** Every turn re-handshakes and
  resends context; long-call latency and cost characteristics have not been
  optimized.
- **A single utterance has a hard cap.** The segmenter force-splits at 30
  seconds, so a long monologue becomes multiple turns rather than an error.

**The third mode, Realtime, is the deliberate exception.** It is a persistent
full-duplex session over a vendor Realtime protocol (OpenAI Realtime GA; Qwen-Audio
Realtime on DashScope): uplink is continuously streamed, turn boundaries come from
server VAD, and interruption mixes local-VAD playback stop with per-vendor
protocol semantics (OpenAI: supplemental `conversation.item.truncate`; Qwen:
server-side auto-cancel plus a client silent window). It reuses the shared
capture/AEC/playback shell but bypasses the cascade pipeline entirely — see
[Three modes](#three-modes) and
[Providers and capability matrix](#providers-and-capability-matrix).

## Layers and ownership

| Module | Owns | Does not own |
|---|---|---|
| `domain:voice` | Session contract, state snapshot, failure taxonomy, capability-resolution interfaces, session-based synthesis interface | Any Android / ADK / Compose type |
| `core:audio` | Recording, Silero VAD, software AEC (speexdsp JNI), playback reference buffer, audio format contract | Business semantics |
| `data:voice` | Session assembly, AEC/VAD orchestration, PCM playback, device routing, capability registry, foreground service, process-level controller | Model selection, agent execution, vendor adapters |
| `:data:modelaccess` | Vendor model/speech adapters: realtime (`OpenAiRealtimeModel`/`QwenRealtimeModel`), cascade (`CascadedVoiceModel`), ASR/TTS protocols + probes, `VoiceLiveModelFactory` assembly | Devices, AEC, playback, session ownership |
| `data:agent`/`voice` | Voice session runtime: `AdkVoiceAudioBackend`, duplex session facade (`NativeRealtimeDuplexSession`), realtime tool bridge; wiring Chat mode to the full agent | Vendor model adapters (moved to `:data:modelaccess`) |
| `feature:chat`/`realtime` | UI state mapping and forwarding user intent | Session ownership, lifecycle teardown |
| `:app` | Manifest declarations, permissions, notification-permission guidance, navigation return | — |

Ownership rules that matter (`domain/voice/.../RealtimeVoiceRepository.kt`):

- The session is owned by the **process-level call controller**. Leaving the
  page, backgrounding, and destroying the Chat NavEntry must never call
  `close()`.
- Resources are released only when the user explicitly ends the call, either
  from the voice page or from the status-bar notification.
- The recording permission is obtained at the UI entry point. The session layer
  only classifies a missing microphone as a `Microphone` failure.

Two wiring details are worth knowing:

1. At compile time `data:voice` depends only on the `VoiceAudioBackend`
   interface; the implementation is supplied by `VoiceAudioModule` in
   `data:agent` (`AdkVoiceAudioBackend`). So "no model inference inside
   `data:voice`" is not an accident — it is enforced by the layering. (Since the adapter extraction, `data:voice` additionally depends on `:data:modelaccess` for the vendor-owned endpoint helper.)
2. `RealtimeVoiceRepository` is **not** a singleton (each injection yields a new
   instance). The singleton is `RealtimeVoiceCallController`, which holds the
   session on an application-level `CoroutineScope`. That, not the page, is why
   leaving the page does not interrupt the call.

## Deep dives

Secondary detail lives in the sibling files below (Chinese companions under
`spec/zh/voice/`); this document keeps the design intent, the contracts, and
the change checklist:

- [Realtime voice pipeline](pipeline.md) — the end-to-end path: entry and
  permissions, capture, echo cancellation, VAD and segmentation, uplink and
  recognition, generation, synthesis and playback, barge-in, and the four
  typical branches.
- [Realtime voice state, devices, and lifecycle](runtime.md) — the state
  machine and generation counters, audio device routing, background survival,
  and the reliability rules.
- [Realtime voice test coverage](testing.md) — what each automated test
  guards, and what only hardware can verify.

## Three modes

| Dimension | Simple | Chat | Realtime |
|---|---|---|---|
| Default | Every call starts here | Requires explicit switch | Requires explicit switch |
| Engine | Cascaded voice model | Full chat agent (ADK Runner) | Native Realtime model via `backend.openDuplex()` (`OpenAiRealtimeModel` / `QwenRealtimeModel`, dispatched by serviceId) |
| Context | 32 in-memory pairs in `heardHistory`, process-local | Full history in the ADK Session | Server session; **a reconnect is a new session, context is not replayed** |
| Heard granularity | Only **fully played-out** sentences enter context | Runner persists complete model events | None — transcripts are caption-only |
| Tools | None | Same as text chat; every confirmation auto-approved | Realtime: none. RealtimeAssistant: client-executed function tools from the authorized local/MCP/plugin/app-function/sandbox sources, every confirmation auto-approved (see [Realtime assistant tools](#realtime-assistant-tools)) |
| Persistence | None | Runner writes the Session; this layer only notifies | None |
| Occupancy | None | `AgentRuntimeGate` lease (`REALTIME_VOICE`) | None |
| Preconditions | — | Requires an existing `conversationId` + `modelSelection` | Requires a configured default Realtime model (see [Default Realtime selection](#default-realtime-selection)); entry is guided to the default-models page when none is configured |

`RealtimeVoiceConfiguration` hard-codes Chat's and Realtime's preconditions with
`require`: **no implicit conversation creation**. The model selection stays
fixed for the duration of the call.

### Realtime assistant tools

RealtimeAssistant is Realtime plus a **client-executed tool bridge**
(`RealtimeAssistantTools` / `RealtimeVoiceToolBridge` in `:data:agent`). Facts that
matter before changing it:

- **Declaration flow.** The bridge is resolved once per call inside
  `VoiceAudioBackend.openDuplex()` (on `Dispatchers.IO` — MCP servers are probed
  there, so a broken server can add up to the 8 s probe budget to call start).
  Declarations ride into the provider's `session.update` through the ADK
  `GenerateContentConfig.tools` seam; the wire shapes differ per vendor (OpenAI GA:
  flat `{type, name, description, parameters}`; Qwen: the declaration nested under a
  `function` key). Resolving again per reconnect never happens — the bridge survives
  reconnects, and a reconnect is a new server session, so tools are re-declared in the
  new session's `session.update` automatically.
- **Source whitelist.** The bridge reuses the same `AgentContribution` sources as the
  chat agent, filtered by an allowlist in `RealtimeAssistantTools`:
  `local` (authorization-page switch + confirmation-required tools declared, mobile-use
  included), `mcp` (no conversation config → global enabled servers), `plugin`,
  `appfunction` (its per-call toolsets are the only bridge-owned resources, closed on
  session end), `sandbox`. Excluded on purpose: `base` (session/artifact/memory and
  user-input suspension tools need the ADK runner), `skill` (sandbox orchestration),
  `official` (vendor-declared/remote-executed declarations throw on local run),
  `model` (catalog tools are irrelevant mid-call). A new contribution must be added to
  the allowlist consciously.
- **Execution context.** There is no ADK runner and no persisted session. Execution
  runs on a bare `ToolContext` over a minimal `InvocationContext` whose `RunConfig`
  metadata carries `ToolRunMetadata` (model runtime + a per-call `mobile_use` owner);
  session state, artifacts, and confirmations have no backing store. `FunctionTool`'s
  confirmation gate is satisfied by always passing `ToolConfirmation(confirmed = true)`
  — voice calls auto-approve, same policy as voice Chat mode.
- **Round flow (both vendors).** `output_item.added(function_call)` starts the buffer;
  `function_call_arguments.delta` accumulates; `.done` finalizes. If `.done` never
  arrives, unfinished calls are flushed when `response.done` lands (covers OpenAI's
  "read arguments from response.done" path) for completed responses, for a missing
  status, and for an unknown vendor status (logged). Only statuses that explicitly
  mean the response did not complete (`cancelled` / `canceled` / `incomplete` /
  `failed`) **discard** pending calls, as does the `speech_started` barge-in path:
  truncated arguments are never executed and never written back. The facade
  (`NativeRealtimeDuplexSession`)
  runs the round in a child coroutine — receiving is never blocked — serializes rounds
  with a mutex, writes each result back as `conversation.item.create` /
  `function_call_output`, then sends one `response.create` per round. Function-call
  arguments are never spoken (no TTS), and a disconnect cancels in-flight rounds
  (reconnect = new session).
- **UI feedback.** `RealtimeVoiceEvent.ToolCallStarted` maps to
  `RealtimeVoiceState.toolNotice`; the captions show a "calling tool" pill. The state
  machine keeps `Thinking` while a tool round is pending (`toolNotice` non-null at
  `response.done`), so the second round's transcript deltas keep appending; the notice
  clears when the second round's first transcript delta or audio chunk arrives, on
  interrupt, on reconnect, or on the next user turn.

### Default Realtime selection

The Realtime model is chosen in **Default models**, not in the call. The page
carries a `Realtime` section with two rows: the Realtime model and the
Realtime session voice.

| Fact | Rule |
|---|---|
| Candidate scope | Only services that are enabled, keyed, and **declare a native Realtime adapter** contribute candidates. A Realtime-marked model without an adapter silently degrades to the cascade path (which needs STT/TTS capabilities it does not declare), so it is hidden rather than shown-disabled. |
| Voice table | None is built. Each vendor adapter declares one **default voice per model** (`longanqian_v3.1` on Qwen 3.1, `longanqian` on Qwen 3.0, `cedar` on OpenAI) plus whether a free-text override is accepted. Qwen accepts a cloned `voice_id`; OpenAI's set is fixed, so it offers a dropdown only. |
| Voice binding | A custom voice remembers the model it was set for. Because the legal voice sets differ per model generation, the override stops applying when the selected model differs and the model's default voice takes over. Clearing the **model** keeps the stored value, so re-selecting the same model restores it. |
| Availability | The default selection is resolved at mode-switch time. A selection whose service was deleted, lost its key, lost the model, or lost its native adapter counts as **not configured**: Realtime mode is unavailable and the entry guides to the default-models page (opening the Realtime picker directly). |
| Duration | The selection is fixed for the duration of a call. Changing the default mid-call only affects later calls. |

The call-side mode menu shows the configured Realtime model name but offers
**no** model switching: the default page is the single entry point.

Adapter facts live behind `domain:voice`'s `RealtimeVoiceSupport` contract
(service id, uplink contract, default voice, built-in voices, custom-voice
policy), contributed by each native adapter in `:data:modelaccess` and aggregated by
`VoiceCapabilityRegistry`. `VoiceLiveModelFactory` therefore reads the uplink
sampling rate and the default voice from the adapter instead of branching on
the service id.

### Mode switching

The ordering in `selectMode()` must not change:

1. Up-front rejection (same mode / already switching / Chat unavailable /
   `Persistence` failure — the last one is rarely reachable, see
   [Known gaps](#known-gaps)).
2. `generation++`, `switching = true` (this is the **controller's own** generation,
   used to discard stale startup results; unrelated to the playback generation).
3. `startup?.cancelAndJoin()` → `finishPendingTurn()`; **on failure only
   `switching` is reset and the original session is kept** for retry.
4. Cancel state collection → `close()` → re-check `generation` and the `session`
   identity → `create(mode)`.
5. Switching back to Simple passes a null `conversationId`.

Step 3's "keep the original session on failure" is deliberate: switching modes
without submitting the text silently drops a turn.

## Providers and capability matrix

First, a clarification on two easily confused capability names: **STT and ASR are
not two words for the same thing, they are two capability slots.** STT means a
dedicated whole-utterance transcription service; ASR means reusing a chat model
for recognition. When a model has both, the dedicated STT wins (more accurate,
shorter path).

| Service ID | Recognition | Streaming recognition | Synthesis | Session-based synthesis | Voices | Connectivity probe |
|---|---|---|---|---|---|---|
| `qwen-audio` | Dedicated STT | Yes | Yes | No | Cascade: table per model; realtime: model default (`longanqian_v3.1` on 3.1, `longanqian` on 3.0) plus a free-text override | Realtime WS handshake, 15 s |
| `minimax-speech` | STT (multipart) | No | SSE | Bidirectional WS | Remote + local fallback | No dedicated probe registered |
| `mimo-speech` | STT + ASR | No | Yes | No | Static table | `GET /models`, 15 s |
| `openai-speech` | Dedicated STT | No | Yes (binary PCM stream) | No | Cascade: static table; realtime: built-in table (`OpenAiRealtimeVoices`, default `cedar`), no custom voice accepted | Realtime WS handshake, 15 s |
| `deepseek` / `openai` / `kimi` / `glm` | STT + ASR | No | Yes | No | Empty | Covered by the chat service probe |

DashScope cascade has no session-based synthesis for a concrete reason: Bailian
bills per connection, so reusing a long-lived connection buys nothing.

**Realtime and cascade models share one configuration per vendor.** There is no
separate Realtime service kind: the realtime models live inside their vendor's
speech service under a "实时语音对话" group, carrying `ModelCapability.Realtime`
(and no STT/TTS/ASR markers, so cascade selection never picks them). The
realtime sessions are still persistent full-duplex — server VAD owns turn
boundaries; the model hears and speaks audio natively. Qwen's realtime uses the
OpenAI-derived event protocol where the persistent session is the product shape
itself.

**The native Realtime seam now exists.** `VoiceLiveModelFactory.create()`
dispatches on the selected model's `Realtime` capability marker: the vendor
speech services route `openai-speech` → `OpenAiRealtimeModel`, `qwen-audio` →
`QwenRealtimeModel` (both ADK `Model`s implementing `connect()`/`LiveConnection`),
and `CascadedVoiceModel` otherwise. Key facts about the native path:

- The vendor adapters live in `:data:modelaccess` next to `CascadedVoiceModel`; the
  transport kernel `VoiceWsKernel` and the `@SpeechWebSocketClient` qualifier
  were promoted to `:core:network` (two real consumers across two modules). The
  handshake-failure classification is injected at construction
  (`voiceHandshakeFailure` in `domain:voice`) because the kernel only knows HTTP
  status codes.
- The repository never sees ADK types: `VoiceAudioBackend.openDuplex()` returns
  a `RealtimeVoiceDuplexSession` facade that owns the connection lifecycle
  (config ack gating, bounded backoff reconnects, reconnect-as-new-session) and
  translates ADK responses into `RealtimeVoiceEvent` domain events.
- Uplink is 16 kHz capture shaped by the **per-vendor uplink contract**
  (`VoiceLiveModelFactory.realtimeUplink()`): OpenAI linearly resamples to
  24 kHz (`Pcm16Resampler` in `:core:audio`), Qwen feeds 16 kHz natively. The
  shaped stream enters the facade `NativeRealtimeDuplexSession` (vendor-neutral,
  renamed from `OpenAiRealtimeDuplexSession`). During assistant playback the
  repository gates frames by local Silero VAD with a 12-frame pre-roll ring
  (**hybrid gating**), so residual echo cannot fake a server-side barge-in.
  Before `openDuplex()` resolves (the assistant tool-bridge probe window), frames
  go into a bounded FIFO: VAD reuses its frame buffer, so every frame is copied on
  enqueue; a single consumer coroutine sends them in order once the session is
  ready, and a failed session drops the backlog.
- Barge-in semantics are per vendor: OpenAI relies on server-side
  `interrupt_response` auto-cancel and supplements `conversation.item.truncate`
  on `speech_started` (approximating the truncation point by bytes sent); Qwen
  relies purely on server-side auto-cancel (no truncate/cancel events sent) and
  the model layer keeps a **silent window** — audio deltas after
  `speech_started` are dropped until the next `response.created`.
- Captions use the ADK transcription channels: `inputTranscription` for the
  user side (OpenAI server-side `gpt-4o-transcribe` finals; Qwen adds a `delta`
  increment of confirmed cumulative text, rendered with replace semantics, plus
  the `completed` final), `outputTranscription` deltas for the assistant side
  (Qwen's `transcript.done` is ignored when deltas were seen, preventing
  duplicated captions under append semantics). The client never runs ASR/TTS
  itself.
- Service kind is `ModelServiceKind.Realtime` (third kind). Realtime models
  declare no STT/TTS/ASR capability, so cascade pickers never select them.

### Capability resolution

`VoiceCapabilityRegistry` keys on **`(serviceId, capability)`**:

- Synthesis matches on `serviceId` alone; recognition requires both `serviceId`
  and `capability` to hit.
- A missing streaming-entry returns null and the caller degrades to one-shot
  recognition — **a soft failure, not an exception**.
- `LLM` is the **only exception**: always true, because this layer does not judge
  model availability. For everything else, a declared but unregistered
  `(serviceId, capability)` pair fails explicitly — do not infer leniency for
  STT/TTS/ASR from `LLM`'s permissive behaviour.

### Failure classification

`RealtimeVoiceFailure` is a **translatable** taxonomy; the UI shows mapped copy
and never a raw exception:

| Category | Origin |
|---|---|
| `Configuration` | `start()` default, `isAvailable()` assertion failure |
| `AudioProcessing` | Silero / AEC construction stage |
| `Microphone` | Audio focus denied, `recorder.start()` failure, recording callback error |
| `Recognition` | Recognition flow error (while the revision is still current) |
| `Generation` | Generation flow error with no more specific category |
| `Synthesis` | One-shot TTS failure that is not a cancellation |
| `Playback` | Playback failure |
| `Persistence` | Target conversation gone when entering Chat mode (thrown by `acquire()`); `close()` throwing during submission (phase is already `Closed`). **`finishTurn()` is a no-op, so the "save failed" main path does not exist** |
| `ConversationBusy` | `AgentRuntimeGate` throws `AgentSessionBusyException` |

Below `RealtimeVoiceFailure` sits a **non-mapped** lower-level taxonomy,
`VoiceFailureKind` (`NotConfigured` / `Authentication` / `Network` / `Service` …),
used only by the settings screens and connectivity tests. Do not mix the two: the
call UI cannot see `VoiceFailureKind`.

## Known gaps

The following reflects the actual state of the code, not a to-do list. Confirm
each item is still true before relying on it — or before assuming it was fixed.

1. **The persistence contract is a shell.** `confirmPlayback()` / `finishTurn()` /
   `retryPersistence()` are no-ops in both the Simple and Agent sessions, while
   the `VoiceAudioBackend` interface comments still describe the old semantics
   ("stable segment IDs for deduplication", "keep the event ID so an explicit
   retry can reuse it"). Real consequences:
   - "Save failed" can never happen, so `Persistence` is reachable in only two
     places: the target conversation was deleted when entering Chat mode, and
     `close()` throwing during submission (when the UI is already tearing down).
   - The "retry saving" branch in `retry()` always reports success; the
     `voice_save_failed` / `voice_retry_save` UI paths are unreachable.
   - The "`Persistence` blocks mode switching" assertion in
     `InMemoryRealtimeVoiceCallControllerTest` remains valid, but it verifies the
     **controller's reaction to a failure** (injected fake session), not real
     backend behaviour.
   - This is contract-level debt, not a production bug. The real risk is a
     newcomer trusting the interface comments, assuming playback confirmation and
     persistence retry are implemented, and layering features on these no-ops.
2. **Chat mode's "heard granularity" is not persisted.** The ADK Runner stores
   complete model events, so an interrupted answer reappears verbatim when the
   chat is reopened. Simple mode is the opposite: only played-out sentences enter
   context. The two modes disagree on this point.
3. **`DefaultRealtimeVoiceRepository` has no unit tests.** The playback-echo
   decision itself was extracted into `PlaybackEchoGuard` and is locked down by
   `PlaybackEchoGuardTest`, but the wiring inside the repository (`player.playing`
   / `tailRisk()` checks, reference-level plumbing, the uplink channel race) still
   has no test seam; `VoiceAudioRecorder`, `SoftwareEchoCanceller`, the JNI layer, and
   `AudioTrack` can only be verified on hardware.
4. **Only two native live providers exist (OpenAI and Qwen).**
   `EndpointStrategy.VendorOwned` is now live for `qwen-audio` / `openai-speech`
   (their endpoints are vendor-owned constants; Qwen additionally derives the
   workspace-specific domain from the optional workspace ID). The native seam
   (`VoiceLiveModelFactory` → ADK `Model`) is real but only `CascadedVoiceModel`
   and the two Realtime adapters implement it; a text model implementing `Model`
   still does not automatically gain realtime voice capability.
5. **MiniMax has no dedicated connectivity probe registered**, so its settings
   "test connection" behaviour is unverified.
6. **No control to stop playback manually**; barge-in requires speaking.
7. **Simple mode rebuilds the connection and resends 32 turns of context every
   turn**, so latency and cost for long calls have never been optimized.
8. **The Realtime model and voice are chosen in one place, and that place is
   the default-models page.** Two consequences worth knowing before changing
   anything: there is no per-call fallback (no configured default means the
   mode is simply unavailable, not "pick the first one in the catalog"), and the
   voice is bound to the model it was set for rather than to the service.
   Changing the selection while a call is running does not touch that call.
9. **Tool rounds race barge-in by design.** If the user starts speaking while a
   tool executes, the round still writes its results and sends `response.create`;
   the server may start (and speak) a response mid-barge-in. The repository stops
   playback immediately (local VAD) and clears the tool notice, so the worst case
   is a late answer, not a stuck state. Only device verification covers this.

## Checklist when changing this system

- **Adding a provider** — the complete list (miss any item and something will be
  missing in settings or at runtime):
  1. Implement `SpeechRecognizer` / `StreamingSpeechRecognizer` /
     `SpeechSynthesizer` / `StreamingSpeechSynthesizer`, register with
     `@IntoSet`, and declare `serviceIds` and `capabilities`.
  2. Register `(serviceId, capability)` in `VoiceCapabilityRegistry`. Streaming
     recognition requires an explicit `STREAMING_STT` entry, otherwise it is
     silently downgraded to one-shot.
  3. **Voices**: register the service table in `DefaultTtsVoiceRepository` (cache
     key includes `modelId`) and provide a local fallback voice table.
  4. **Connectivity probe**: implement and register `SpeechConnectionProbe`.
     Without it, the settings "test connection" behaviour for that vendor is
     undefined (which is MiniMax's current situation).
  5. Keep endpoints and authentication in the data layer. Vendor-owned endpoints
     (Qwen, OpenAI speech) are hardcoded official constants resolved in
     `speechRequestBaseUrl` (Qwen rewrites the host per optional workspace ID);
     only proxy-style speech services (MiniMax, MiMo) accept a user-configured
     base URL.
  6. Do not introduce a second vendor-selection seam alongside
     `VoiceLiveModelFactory`: cascade providers implement `Model` there, and the
     native Realtime path is the same factory dispatching on the model's
     `Realtime` capability marker to `OpenAiRealtimeModel` / `QwenRealtimeModel`
     (unknown vendors fall back to cascade). The repository-facing
     `RealtimeVoiceDuplexSession` facade (`NativeRealtimeDuplexSession`,
     vendor-neutral) is the app-side contract for full-duplex modes, not a
     vendor seam.
  7. Add contract tests, and confirm timeouts run on a real dispatcher rather
     than virtual time.
- **Adding a native realtime provider** (full-duplex, on top of the cascade
  checklist):
  1. Implement an ADK `Model` whose `connect()` opens one persistent wss session
     and gates audio uplink on the config ack; never stream audio before
     `session.updated`.
  2. Extend the vendor's speech service in `SpeechModelConfigs` with a realtime
     model group (models carry `ModelCapability.Realtime`, no cascade markers),
     and register the wss-handshake probe for that service. Endpoints stay
     vendor-owned: constants live in `SpeechEndpoints`, and Qwen derives the
     workspace-specific domain from the optional workspace ID field.
  3. **Declare the adapter's capabilities** by implementing
     `domain:voice`'s `RealtimeVoiceSupport` (service id, uplink contract,
     default voice per model, built-in voices, custom-voice policy) and
     contributing it with `@IntoSet`. Without the declaration the service's
     Realtime models never appear as default-selection candidates — which is
     the point: an undeclared adapter would silently degrade to cascade.
  4. Route through `VoiceAudioBackend.openDuplex()` — the facade owns reconnect
     backoff and reconnect-as-new-session; the repository only consumes
     `RealtimeVoiceEvent` and feeds 16 kHz frames into `sendAudio`.
  5. Respect the uplink gating contract: continuous when idle, local-VAD gated
     with the 12-frame pre-roll during playback (`UPLINK_GATE_THRESHOLD` in
     `DefaultRealtimeVoiceRepository`).
  6. Protocol JSON is hand-built Gson (one Protocol object per vendor:
     `OpenAiRealtimeProtocol` / `QwenRealtimeProtocol`); the uplink sample rate
     follows the per-vendor contract — OpenAI keeps 24 kHz pcm16 (no native 16k
     pcm input), Qwen feeds 16 kHz natively. Declare the uplink contract in the
     adapter's `RealtimeVoiceSupport`, not in `VoiceLiveModelFactory`.
  7. Barge-in semantics are per vendor: for server-side auto-cancel (Qwen) send
     no truncate/cancel events and drop residual audio in a silent window; only
     send `conversation.item.truncate` when the vendor needs the supplement
     (OpenAI).
- **Changing the realtime assistant tool surface** — edit the source allowlist in
  `RealtimeAssistantTools` (and only there). Anything needing the ADK runner
  (session state, artifacts, user-input suspension, skills) must stay out; a new
  contribution goes through a conscious allowlist decision, not silent inclusion.
  Wire shapes for declarations live per vendor in `OpenAiRealtimeProtocol` /
  `QwenRealtimeProtocol`; `RealtimeVoiceToolBridgeTest` guards execution semantics.
- **Changing VAD thresholds**: `VoiceActivitySegmenterTest` will catch you; also
  confirm the 30-second cap still holds, or a long monologue grows memory
  unbounded.
- **Changing the playback format**: output must remain 24 kHz mono PCM16. Convert
  inside the adapter, or change both the player and the AEC reference geometry
  (`240 @24k ↔ 160 @16k`).
- **Changing routing, AEC convergence, or the playback gate**:
  `onAecReset()` (route switch / session launch) marks a converge window that
  starts at the **next playback start**; later sentences in the same reply do not
  reopen it. During playback and the 400 ms acoustic tail after it,
  `PlaybackEchoGuard` clamps echo-dominated frames to zero VAD probability using an
  echo floor measured from the microphone plus the playback reference level.
  Remove the gate and residual echo becomes a fake "user spoke" event and a
  feedback loop; keep only the window and the second half of a long utterance still
  escapes. The disarm switch must be `player.playing` — the reference level can
  falsely drop to zero when its clock anchor drifts — and the tail after
  `playing == false` must not disarm immediately. The uplink gate must check both
  `player.playing` and `echoGuard.tailRisk()`, never `duplexPlayback != null`, or
  server_vad gets its own independent feedback path. Run `PlaybackEchoGuardTest`
  first — it includes minimal controls for "playback starts inside the window",
  "reverb tail after playback", and "reference-level dropout".
- **Changing session state**: every new async result needs a revision guard,
  otherwise a late result after cancellation leaks into the next turn.
- **Changing domain interfaces or Hilt bindings**: assemble and install a full
  APK, then restart the process while preserving user data before validating.
  Assembling without installing, or applying a HOT_FIX (the project's internal
  hot-patch delivery mechanism) on an older APK, can mix incompatible runtime
  types.
- **Verifying device behaviour**: authentication, playback, and routing need real
  hardware; interface conformance does not prove platform support.

## Glossary

| Term | Meaning |
|---|---|
| ADK (ADK Kotlin) | Google's Agent Development Kit for Kotlin. The inference side of realtime voice runs on its official `Model` / `LiveConnection` interfaces |
| `LiveConnection` | One ADK realtime connection, returned by `Model.connect()`; this project creates a fresh one each turn |
| `CascadedVoiceModel` | An adapter that chains "text model + ASR + TTS" and presents itself as realtime voice; the cascade-path realtime implementation (the native path has separate OpenAI / Qwen Realtime adapters) |
| AEC | Acoustic Echo Cancellation |
| VAD | Voice Activity Detection — the sole source of turn boundaries here |
| DSP | Shorthand for the per-frame chain: AEC, VAD, and segmentation |
| PCM16 | 16-bit signed integer samples. All audio here is little-endian and mono |
| Silero | Open-source VAD model; this project uses v6.2 ONNX |
| speexdsp | Open-source audio processing library providing the software AEC JNI implementation |
| ONNX | Model interchange format, executed by ONNX Runtime |
| SSE | Server-Sent Events; a server-pushed HTTP response stream of events |
| Turn generation | `state.revision`. One Q&A is one generation; async results must match the current generation to be applied |
| Playback generation | `RealtimePcmPlayer.generation`. Incremented on `stop()`, used to discard old-turn audio |
| Lease | `AgentRuntimeGate`'s runtime exclusive access to one chat; only one writer per conversation |
| Foreground service | Android `ForegroundService`; the `microphone` type is what permits background recording |
| Audio focus | Android's audio exclusivity mechanism. Here it is requested but **not** observed for loss |
| Communication device | The call routing device set from `AudioManager.availableCommunicationDevices` |
