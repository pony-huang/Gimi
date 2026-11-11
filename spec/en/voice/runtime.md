# Realtime voice state, devices, and lifecycle

[简体中文](../../zh/voice/runtime.md) · English

> Deep dive of [Realtime voice system](realtime-voice-system.md): the state
> machine and the three generation counters, audio device routing, background
> survival (foreground service and call ending), and the reliability rules —
> timeouts, deliberate non-retrying, backpressure, and cancellation propagation.

## State machine

`RealtimeVoicePhase` transitions:

| From | Event | To |
|---|---|---|
| `Idle` | `start()` succeeded (recording running) | `Listening` |
| `Listening` | An utterance was segmented (already switched back from `Speaking` at onset) | `Transcribing` |
| `Transcribing` | Recognition empty | `Listening` |
| `Transcribing` | Non-empty text | `Thinking` (turn generation +1) |
| `Thinking` | First audio block actually written to `AudioTrack` | `Speaking` |
| `Speaking` | **Segment played out (no exceptions; every segment takes this edge)** | `Thinking` |
| `Thinking` | Segment flow ended and the last segment also played out | `Listening` |
| Any (including `Speaking`) | Local onset / backend `Interrupted` / playback generation invalidated | `Listening` |
| Any | `fail(failure)` | `Failed` (session alive, audio stopped) |
| `Failed` | `retry()` | fresh `start()` → `Listening` |
| Any | `close()` | `Closed` (terminal) |

The table has one cycle worth internalizing: **`Thinking` and `Speaking`
alternate per segment**, it is not a one-shot `Thinking → Speaking`. In code,
`playSegment()` always writes the phase back to `Thinking` after playback, and
only `reply()` writes `Listening` after `playback.join()` returns (segment flow
closed). So "last segment played out" is a two-hop `Speaking → Thinking →
Listening`, never a direct jump.

The three barge-in sources collapse into one row because they do exactly the same
thing: stop playback + cancel generation + clear captions + return to
`Listening`. They differ only in where the trigger originates (local VAD /
backend event / player generation check).

### Why `listening` is separate from `phase`

The domain comment answers this directly: "the phase of speaking and inference;
listening state is stored separately because the microphone must keep capturing
during playback to support barge-in". The code agrees: `speechStarted()` checks
only `closed` / `started` / `muted`, **never `phase`** — that is what makes a
`Speaking` state interruptible (given it is unmuted).

So do not infer "is the mic capturing" from `phase != Speaking`; the two are
orthogonal.

### Three generation counters, do not conflate

| Counter | Incremented when | Discards |
|---|---|---|
| `inputRevision` (session-private `Long`) | Speech onset, mute toggling, failure, `finishPendingTurn`, `close` | Stale **recognition** results |
| `state.revision` (in the state snapshot; "turn generation") | Each successful recognition entering `Thinking` | Stale **generation / playback / caption** results |
| `RealtimePcmPlayer.generation` (inside the player; "playback generation") | `stop()` or a new `play()` | Audio from an old turn **not yet written** |

Terminology warning: in this document **"turn generation" always means
`state.revision`**, which is not the player's `generation`; `inputRevision` guards
only the recognition side. Each layer guards a different segment, and mixing them
up produces wrong guard conditions. Together they ensure late async results after
a cancellation are silently dropped instead of leaking an old answer's captions
or audio into a new turn.

Additionally, `recognize()` calls `replyJob?.cancelAndJoin()` before starting a
new turn — the previous turn must be genuinely cancelled and its text submitted
before the next turn may take backend ownership. That is a correctness
requirement, not a performance trade-off.

## Audio devices and routing

Enumeration comes from `AudioManager.availableCommunicationDevices` (the call
routing device set, not all output devices). Types map to six kinds: speaker /
earpiece / Bluetooth (including BLE and hearing aids) / wired / USB / other.

Selection is a pure function (`VoiceAudioDeviceSelection`):

```text
manual selection if present -> headphones in reverse connect order -> speaker -> first in list
```

"Headphones" here means Bluetooth / wired / USB — **not** speaker or earpiece.

"Connect order" is the first-seen order the controller records itself, not the
platform list order: platform order is unstable and would make "auto" jump to an
unexpected device after a plug event.

Timing details that bite:

- Route changes **reset the DSP** (`echoCanceller.reset()` / `detector.reset()` /
  `segmenter.reset()`), and the reset is dispatched to an IO thread. Doing it on
  the main thread while holding `inputLock` contends with the capture thread's
  per-frame DSP, which shows up as jank.
- `devicesChanged()` refreshes twice: immediately, then **again 1000 ms later**.
  A Bluetooth call endpoint may appear after the physical-device notification.
- Switching has a 30-second confirmation timeout. If it still has not taken
  effect, the preference is cleared, `selectionFailed` is set, and
  `clearCommunicationDevice()` runs.
- Disconnects and switch failures are distinguished **by a monotonically
  increasing `noticeRevision`**; `selectionFailed` is only a label for the latest
  event. Using a Long instead of a Boolean lets two consecutive Bluetooth
  disconnects each raise their own notice.
- `close()` restores the communication device snapshotted at `start()`, ignoring
  late callbacks.

The code never calls `startBluetoothSco()` or `setSpeakerphoneOn()`; Bluetooth
relies purely on `setCommunicationDevice` + `MODE_IN_COMMUNICATION`. Actual
A2DP/SCO switching behaviour can only be verified on real hardware.

## Background and lifecycle

### Why leaving the page does not end the call

`RealtimeVoiceCallController` is a `@Singleton` with its own
`SupervisorJob + Dispatchers.Main.immediate` scope. The session, state
collection, and the teardown chain all live on application-level coroutines, so
page destruction, navigation, and foreground/background changes never cancel
recording, generation, or playback.

Conversely, `RealtimeVoiceViewModel` only derives UI state in `viewModelScope` and
**never holds the session**.

### Foreground service

- Type `microphone` (mandatory for background microphone use since Android 14).
- `START_NOT_STICKY`: no restart after process death. There is no
  cross-process-resumable audio session, and an idle service would just squat on
  the microphone.
- Notification channel `realtime_voice_call_v2` (`IMPORTANCE_DEFAULT`). Channel
  importance is immutable after creation, so upgrading from the legacy
  `realtime_voice_call` requires a new ID, and the old channel is deleted on
  creation.
- The notification snapshot collapses to `VoiceCallNotificationContent` (phase,
  mute, ending, title, backgrounded). Mic levels and streaming captions
  **never** trigger a notification refresh.
- Wake lock `PARTIAL_WAKE_LOCK`, 10-minute expiry, renewed every 5 minutes.
  Renewal failure has **no** special handling: it does not end the call, but the
  process may be frozen after screen-off. This is a known weak point.

Two ordering constraints must hold:

1. **Promote to foreground before deciding to exit.** Otherwise
   `startForegroundService` times out and throws
   `ForegroundServiceDidNotStartInTimeException`.
2. **Attempt foreground promotion in every `onStartCommand`**, not just
   `onCreate`. When the app returns to the foreground, `ensureForeground()` only
   reaches `onStartCommand` for an already-running service; if promotion were not
   repeatable, recovery from a failed first attempt or a system downgrade would be
   impossible.

### Ending a call

There are exactly two entries, and both converge on
`RealtimeVoiceCallController.end()`:

- the in-page **End call** (which requires returning to the voice page via the
  text chat's voice button or by tapping the notification);
- the **End call** action on the status-bar notification.

`end()` is idempotent, and the `ending` state is held until `close()` returns —
the foreground service and wake lock must cover the entire release. The teardown
coroutine runs in `NonCancellable`: neither leaving the page nor starting a new
session may truncate the release of the old microphone ownership.

Release order in the session-level `close()` (the order is itself the contract):

```text
stop playback (player.stop -> generation++)
  -> stop and join recording (recorder.releaseAndJoin)
  -> cancel recognition / join the reply job
  -> release the DSP under inputLock
  -> session.close() (failure degrades to Persistence but does not block the rest)
  -> restore audio focus and routing, restore audio.mode
  -> phase = Closed, cancel the scope
```

Recording must be `cancel()` + `stop()`-ed so the blocking `read()` returns, and
only then `release()`-ed while the IO thread is still parked in `read` — that is
the device-specific crash path.

Audio focus deliberately **does not register a loss listener**: closing the
session because another app briefly stole focus would cause spurious
interruptions, so focus changes are advisory only.

## Reliability

### Timeouts

| Location | Timeout | Purpose |
|---|---|---|
| DashScope awaiting `task-started` | 15 s | With heartbeats, a "half-dead" server never surfaces as a disconnect |
| DashScope synthesis `task-started` | 15 s | Same |
| MiniMax bidi `task_started` | 15 s | Close the connection on timeout |
| MiniMax bidi `task_flushed` | 10 s | Throws `IOException` on timeout |
| MiniMax bidi teardown | 3 s | Wait for `task_finished` |
| Route confirmation | 30 s | Roll back if not applied |
| Connectivity probe | 15 s | Handshake / `GET /models` |

Every timeout is deliberately wrapped in `withContext(Dispatchers.IO)`: a comment
states that the limit must run on a real dispatcher rather than the caller's
virtual-time test dispatcher, so no matter what context the caller is in, the
timeout falls back to a real clock.

### Not retrying is deliberate

Recognition and synthesis **never retry**, and the reason lives in code comments
worth preserving:

- Recognition bills by **audio duration**, so replaying the audio double-bills.
- Synthesis bills by **character**, so replaying the same text double-bills.

Hence the WebSocket client sets `retryOnConnectionFailure(false)` globally and
each HTTP client disables it too. The only "auto-reconnect" is really a fresh
connection per turn.

The only user-visible retry is `RealtimeVoiceSession.retry()`, available **in
`Failed` state only**. It rebuilds the recording stream and reopens the DSP rather
than merely resuming playback.

### Backpressure

| Layer | Mechanism | Rationale |
|---|---|---|
| WS inbound frames | `Channel(UNLIMITED)` | A bounded queue with drops would silently lose audio frames, which is harder to diagnose than a backlog |
| WS outbound audio | `delay(10)` once the queue exceeds 1 MB | Prevents uplink buildup |
| Simple-mode audio segments | `Channel(RENDEZVOUS)` | Production is authorized only when collection starts, and cancelling playback wakes a blocked connection |
| Between segments (session layer) | `Channel(1)` | Genuine serial playback across segments |
| Playback writes | Non-blocking + `delay(5)` | No stale audio after cancellation |
| Cascaded delivery | `Channel(RENDEZVOUS)` + `await` | A returned `emit` means the consumer accepted it, preventing synthesis from running ahead or accumulating billable requests |

### Cancellation must propagate unchanged

`CancellationException` is never mapped to a business failure. Three concrete
practices:

- `core:common/cancellationAwareRunCatching` catches cancellation before generic
  exceptions.
- `CascadedVoiceModel` expresses interruption with `TurnInterrupted` /
  `ConnectionClosed`, both extending `CancellationException`, and calls
  `ensureActive()` before emitting the `interrupted` event — so a genuine
  cancellation is never mistaken for a server-side interrupt.
- All `close()` teardown runs in `NonCancellable`; cancellation cannot truncate
  resource release.
