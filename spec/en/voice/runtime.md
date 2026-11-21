# Realtime voice state, devices, and lifecycle

[简体中文](../../zh/voice/runtime.md) · English

> Companion to [Realtime voice system](realtime-voice-system.md). Keeps only what
> code cannot tell you: why the state/counter split exists, routing and lifecycle
> gotchas, and reliability contracts. Names, tables, and exact timeouts: read the code.

Audience: coding agents working on Gimi's realtime voice feature.

## State machine

Read the current `RealtimeVoicePhase` transitions in code. Two facts to internalize:

- `Thinking` and `Speaking` alternate **per segment**, not once per turn. Each
  played segment writes the phase back to `Thinking`; only the end of the segment
  flow writes `Listening` — a two-hop `Speaking → Thinking → Listening`.
- Barge-in (local onset / backend `Interrupted` / playback generation check) is
  one action: stop playback, cancel generation, clear captions, return to
  `Listening`; only the trigger origin differs.

`listening` is **orthogonal to `phase`** and stored separately: the mic keeps
capturing during playback for barge-in. `speechStarted()` checks only
`closed`/`started`/`muted`, never `phase` — never infer mic state from `phase != Speaking`.

## Three generation counters — do not conflate

| Counter (code name) | Guards against |
|---|---|
| `inputRevision` (session-private `Long`) | stale **recognition** results |
| `state.revision` ("turn generation") | stale **generation / playback / caption** results |
| `RealtimePcmPlayer.generation` ("playback generation") | old audio **not yet written** |

"Turn generation" here always means `state.revision`, not the player's
`generation`; `inputRevision` guards only recognition. Each guards a different
segment — mixing them writes the wrong guard. Together they silently drop late
async results after cancellation instead of leaking an old answer's audio into a new turn.

`recognize()` calls `replyJob?.cancelAndJoin()` before a new turn: the old turn
must be genuinely cancelled and its text submitted before the next owns the
backend. Correctness, not optimization.

## Audio devices and routing

Enumeration is `AudioManager.availableCommunicationDevices` (call-routing set,
not all outputs). Selection is a pure function (`VoiceAudioDeviceSelection`) —
read it for the rule. "Headphones" means Bluetooth / wired / USB, **not** speaker
or earpiece.

Gotchas:

- "Connect order" is the controller's own first-seen order, **not** platform order
  (unstable → "auto" would jump devices after a plug event).
- Route changes reset the DSP on an **IO thread**; on the main thread under
  `inputLock` this contends with per-frame capture DSP and shows up as jank.
- `devicesChanged()` refreshes twice — now and again 1000 ms later — because a
  Bluetooth call endpoint can appear after the physical-device notice.
- Switching has a confirmation timeout; on failure clear preference, mark
  `selectionFailed`, call `clearCommunicationDevice()`.
- Disconnects vs. switch failures use a **monotonic `noticeRevision`** (Long, so
  two consecutive BT disconnects each raise a notice); `selectionFailed` only
  labels the latest event.
- `close()` restores the device snapshotted at `start()`, ignoring late callbacks.
- No `startBluetoothSco()` / `setSpeakerphoneOn()`; Bluetooth relies solely on
  `setCommunicationDevice` + `MODE_IN_COMMUNICATION`. Real A2DP/SCO behavior needs
  on-device verification.

## Background and lifecycle

A call survives leaving the page because the `@Singleton`
`RealtimeVoiceCallController` owns the session and teardown on application-scoped
coroutines; page, navigation, and foreground changes never cancel them. The
`ViewModel` only derives UI state and **never holds the session**.

Foreground service:

- Type `microphone` (required for background mic use since Android 14).
- `START_NOT_STICKY`: no cross-process-resumable session exists, and a restarted
  idle service would just squat the microphone.
- Channel ID is immutable after creation — raising importance needs a **new ID**;
  the legacy channel is deleted at creation.
- Promotion must (1) happen **before deciding to exit** (else
  `startForegroundService` times out) and (2) run in **every `onStartCommand`**,
  not just `onCreate`, so a failed first attempt or system downgrade can recover.
- Wake lock renewal failure has no special handling: the call continues but the
  process may freeze after screen-off — a known weak point.

Ending a call: every hang-up path converges on the controller's `end()`; navigation
waits for `ending` to clear. `end()` is idempotent; `ending` is held until `close()`
returns and teardown runs in `NonCancellable`, so nothing truncates release of the
old mic ownership. The **release order in `close()` is itself the contract** (see
code): stop playback → stop/join recording → cancel recognition and join the reply
job → release DSP → `session.close()` → restore focus, routing, and mode → `Closed`,
cancel scope. Recording must be `cancel()`+`stop()`-ed so blocking `read()` returns,
then `release()`-ed while the IO thread is still in `read` — the device-specific
crash path. Audio focus has **no loss listener**: a brief steal must not spuriously
end the call, so focus changes are advisory only.

## Reliability

Timeouts span DashScope/MiniMax handshakes, route confirmation, and connectivity
probes (locations and exact values live in code):

| Location | Why a deadline is needed |
|---|---|
| Recognition & synthesis `task-started` | heartbeats keep a half-dead server from surfacing as a disconnect |
| Bidi `task_started` / `task_flushed` / teardown | bound connection setup, flush, and finish waits |
| Route confirmation | roll back a switch that never takes effect |
| Connectivity probe | bound handshake / `GET /models` |

**Every timeout must run on a real dispatcher**, never the caller's virtual-time
test dispatcher — each is wrapped in `withContext(Dispatchers.IO)` to fall back to
a real clock.

Not retrying is deliberate: recognition bills by **audio duration**, synthesis by
**character**, so replay double-bills. WS/HTTP clients disable connection failure
retries; the only "reconnect" is a fresh connection per turn. The sole user-visible
retry is `Session.retry()` in `Failed` state, which rebuilds the recording stream
and DSP rather than resuming playback.

Backpressure (rationale — see code for channel types): inbound frames use an
unbounded queue because dropping silently loses audio and is harder to diagnose
than a backlog; outbound audio delays past a size threshold to stop uplink buildup;
segment delivery and cascaded `emit`/`await` are serialized so a returned `emit`
means the consumer accepted it — no synthesis running ahead or accumulating billable requests.

Cancellation must propagate unchanged: never map `CancellationException` to a
business failure. Catch cancellation before generic exceptions
(`cancellationAwareRunCatching`); express interrupts with `CancellationException`
subtypes and call `ensureActive()` before emitting the interrupt event (a real
cancel is not a server interrupt); run all `close()` teardown in `NonCancellable`.
