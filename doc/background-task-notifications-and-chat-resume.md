# Background Task Notifications and Chat Resume

> Living feature document. Add new background-execution, notification, rendering, or scroll-related findings to the **Incident Log** and **Regression Checklist** sections instead of fixing the same class of issue in isolation.

## Scope

This document describes the behavior contract for agent tasks that continue while the application is backgrounded, including:

- foreground-service execution and process lifetime;
- task completion and user-interaction notifications;
- notification permission and heads-up behavior;
- chat stream resumption after the app returns to the foreground;
- Markdown streaming state and message history restoration;
- keeping the chat list at the correct scroll position.

The same rules apply to both chat tasks and system-assistant tasks unless a section explicitly says otherwise.

## User-facing behavior

### When the app is in the foreground

- Streaming text is rendered incrementally.
- Tool execution, confirmation, text input, and choice cards are rendered in the UI.
- A successful task completion must not produce a notification or a visible Markdown re-parse flash.
- The existing bottom-follow behavior must remain unchanged.

### When the app is in the background

- The task continues through `AgentExecutionService` and `AgentRuntimeGate`.
- The following events may produce a notification when notification permission and system notification settings allow it:
  - tool execution;
  - tool confirmation required;
  - free-text input required;
  - choice required;
  - task completed.
- Returning to the app must show the complete persisted response, not a stale or truncated streaming buffer.
- After a completed background task is restored, the chat list must scroll to the bottom anchor.
- Returning to the foreground must clear stale ongoing interaction notifications.

## Architecture and ownership

### Notifications module

The reusable notification implementation lives in `:core:notifications`:

- `AppNotificationManager` is the application-facing interface.
- `AndroidAppNotificationManager` owns Android notification channels, permission checks, heads-up configuration, and launch `PendingIntent`s.
- `NotificationsModule` binds the implementation through Hilt.

Consumers currently include:

- `feature:chat` / `ChatViewModel`;
- `data:assistant` / `DefaultAssistantSessionCoordinator`;
- `app` / `MainActivity` for clearing interaction notifications when the app starts.

Do not create notification channels or call `NotificationManager` directly from feature code. Add a semantic method to `AppNotificationManager` when a new business event needs notification support.

### Runtime execution

`AgentRuntimeGate` tracks active work by source, session, and phase. `AgentExecutionService` keeps the process alive while the gate is busy. The service notification is separate from user-facing task-event notifications.

The task lifecycle is represented by phases such as:

- `GENERATING`;
- `EXECUTING_TOOL`;
- `WAITING_FOR_CONFIRMATION`;
- `WAITING_FOR_INPUT`.

Notification decisions must be made at the semantic event boundary, not from UI composition. UI visibility is checked by the notifications module through `ProcessLifecycleOwner`.

### Chat stream rendering

`ChatSessionRuntime` owns:

- the authoritative in-memory `Message` list;
- the task/job state;
- per-`TextPart` delta channels;
- session completion/attention state.

`AgentEventReducer` updates the authoritative messages and emits deltas. `ChatTextContent` consumes those deltas through `StreamingMarkdownState`.

The authoritative message text and the local Markdown streaming state are different layers. A fix that updates one layer must explicitly consider the other.

## Non-negotiable invariants

1. **Permission is a prerequisite.** A notification call cannot bypass `POST_NOTIFICATIONS`, the app-level notification switch, or OEM notification policy.
2. **Channel importance is immutable after creation.** Changing the importance in source code does not upgrade an already-created channel.
3. **The persisted/history message is authoritative after a background resume.** Never trust a possibly stale Compose streaming buffer over a freshly loaded message.
4. **Normal foreground completion should be visually continuous.** Do not recreate Markdown state or start a second scroll animation merely because `partial` changed to `false`.
5. **A channel replacement means a new producer stream.** When a channel is reseeded, reset the local streaming Markdown state to prevent duplicate text or stale text.
6. **Background restoration and ordinary completion are different paths.** A workaround for background restoration must not run on every foreground completion.
7. **Scroll requests must be explicit.** Message content changes alone are not a reliable scroll trigger; use a dedicated request token for restoration-driven scrolling.
8. **Do not reload a live task from history.** While a task is active, reseed its delta channels instead of replacing its messages from storage.

## Notification contract

### Permission and system settings

The app declares `android.permission.POST_NOTIFICATIONS`, but declaration alone is not a grant. On Android 13+ and OEM variants, users must grant the runtime permission and may separately enable:

- the app notification switch;
- the notification channel;
- heads-up/floating/banner notifications;
- sound/vibration or lock-screen display.

When debugging a report that notifications are not visible, inspect the device before changing code:

```text
adb shell dumpsys notification --noredact
```

For this app, an entry such as the following means the system is suppressing the app before channel behavior matters:

```text
AppSettings: github.ponyhuang.gimi (...) importance=NONE
```

The expected user action on Flyme is generally:

```text
Settings -> Notifications and status bar -> App notifications -> Gimi
```

Enable notifications and floating/banner notifications there.

### Heads-up notifications

User-facing task notifications use the `app_events_v2` channel because Android channel importance cannot be upgraded in place. The channel is configured with:

- `NotificationManager.IMPORTANCE_HIGH`;
- `NotificationCompat.PRIORITY_HIGH`;
- default sound/vibration behavior;
- an app-launch content intent.

A HIGH channel is necessary but not sufficient for floating notifications: the user and the OEM may still disable floating notifications at the app or channel level.

If the notification behavior changes materially, use a new channel ID rather than assuming an existing channel will adopt the new importance. Document the migration and the user setting required for existing installs.

### Notification lifecycle

- Interaction events share one ongoing notification slot so tool execution is replaced by the current confirmation/input/choice state rather than accumulating stale notifications.
- Completion cancels the ongoing interaction notification before posting the completion notification.
- `MainActivity.onStart()` clears ongoing interaction notifications after the user returns to the app.
- Notification methods should remain safe no-ops when permission or app notification settings are disabled.

## Chat resume contract

### Active task on resume

When `ChatAction.ResumeChat` sees an active runtime:

1. close and recreate the per-part delta channels;
2. seed each partial text part with its current authoritative text;
3. publish the runtime;
4. do not reload history while the task is still active.

`ChatTextContent` keys its local streaming state by the channel identity. This is intentional: a replacement channel represents a reseeded stream and must not append the complete seed to the old stream state.

### Completed task on resume

When `ChatAction.ResumeChat` sees an inactive runtime:

1. load the session messages from `ConversationRepository`;
2. only replace the runtime when the loaded result is usable (do not replace non-empty in-memory content with an accidental empty result);
3. close old part channels;
4. publish the authoritative history;
5. increment `ChatUiState.scrollToLatestRequest`.

The dedicated scroll request is important. The number of rendered list items can remain unchanged when a truncated message is replaced by its complete version, so item-count changes alone cannot trigger restoration scrolling.

### Markdown state rules

`ChatTextContent` must follow these rules:

- During one continuous stream, retain the local streaming state to avoid re-parsing and visual flashes.
- When the producer channel is replaced, recreate the local streaming state.
- On ordinary completion, keep the existing state and allow the completed content update to settle without an extra re-parse.
- On background restoration, the closed/replaced channel causes a reset and the complete message is rendered from authoritative history.

Do not add `partial` as an unconditional key unless the UX impact has been verified. Doing so can make every normal foreground completion flash.

## Scroll behavior

`ChatScreen` has two separate scroll responsibilities:

1. **Normal live follow:** while the user is at the bottom and a task is running, follow stream growth.
2. **Explicit restoration scroll:** when `scrollToLatestRequest` changes, animate to `chat-bottom-anchor` even if the rendered item count did not change.

Do not use the complete `messages` list as a general-purpose `LaunchedEffect` key. It changes frequently during streaming and can cause repeated scroll animations, stealing the user’s position or producing visible flicker.

When changing list rendering, preserve:

- stable item keys (`answer:<turnId>:<messageId>` and `chat-bottom-anchor`);
- the distinction between user-controlled scrolling and automatic bottom following;
- the explicit restoration request path.

## Known failure modes and diagnosis

### Notifications are suppressed

**Symptoms**

```text
Suppressing notification from package github.ponyhuang.gimi by user request.
```

**Diagnosis**

Inspect `dumpsys notification`. `importance=NONE` at the app level normally means the runtime permission or app notification switch is disabled. Check OEM floating/banner settings separately.

**Do not fix by** removing the permission check or repeatedly posting notifications. The system will continue suppressing them.

### Background response is truncated until navigation

**Cause**

The UI retained a `StreamingMarkdownState` that had consumed only part of the response. Navigating away and back accidentally reloaded history and hid the bug.

**Required fix pattern**

- reload complete history only for an inactive runtime on resume;
- close stale channels;
- key streaming state by channel identity;
- issue a dedicated scroll request.

### Background response is complete but the list stops near the user message

**Cause**

The restored message count is unchanged, so an item-count-based scroll effect does not restart.

**Required fix pattern**

Increment `ChatUiState.scrollToLatestRequest` after the completed-history reload and include only that token in the restoration scroll effect.

### Foreground completion flashes

**Cause**

A restoration workaround was applied to every completion, commonly by:

- keying Markdown state by `partial` for all completions;
- keying a scroll effect by the entire message list;
- running both a normal item-count scroll and a restoration scroll.

**Required fix pattern**

Keep normal completion on the existing stream/channel path. Trigger channel reset and explicit scroll only in the background-resume branch.

## Regression checklist

Before changing this area, test all scenarios below on a real device or representative OEM emulator:

- [ ] Foreground generation of a short response does not flash at completion.
- [ ] Foreground generation of a long Markdown response does not flash at completion.
- [ ] Foreground completion keeps the list at the bottom.
- [ ] User scrolling upward during a live task is not forcibly pulled back down.
- [ ] Background generation completes and produces a task-completion notification.
- [ ] Background tool execution replaces the ongoing task notification rather than creating stale duplicates.
- [ ] Background confirmation produces a heads-up notification and opens the app when tapped.
- [ ] Background free-text input produces a heads-up notification.
- [ ] Background choice input produces a heads-up notification.
- [ ] Returning to the app clears ongoing interaction notifications.
- [ ] Returning while a task is still active continues the stream without duplicate text.
- [ ] Returning after completion shows the full persisted response.
- [ ] Returning after completion scrolls to the bottom anchor even when the item count is unchanged.
- [ ] Switching conversations still loads complete history.
- [ ] Opening Settings and returning does not change the final chat scroll position.
- [ ] Notifications remain safe no-ops when permission is denied.
- [ ] Existing installations receive the intended notification-channel migration behavior.

## Change protocol for future fixes

When modifying this feature:

1. Identify whether the change belongs to the notification lifecycle, runtime lifecycle, authoritative history, Markdown state, or scrolling layer.
2. Preserve the separation between foreground completion and background restoration.
3. Add or update a focused regression test before broad refactoring.
4. Run the relevant JVM/Compose tests where available and perform a full Android build.
5. Exercise at least one foreground and one background scenario on a device.
6. Append a short entry to the Incident Log below, including the symptom, root cause, and guard added.

## Incident Log

### 2026-09-20/21 — Notification permission and heads-up behavior

- **Symptom:** Notifications appeared to be ineffective; system logs reported that notifications were suppressed.
- **Root cause:** The device had app-level notification importance `NONE`; the notification permission/app notification switch was disabled. Later, heads-up behavior also required a HIGH-importance channel and Flyme floating-notification permission.
- **Guard:** Keep permission/system-setting checks, use a versioned HIGH-importance `app_events_v2` channel, and document the OEM settings required for floating notifications.

### 2026-09-20/21 — Truncated response after background completion

- **Symptom:** Returning from the background showed only part of a completed response. Switching conversations or opening Settings and returning made the full response appear.
- **Root cause:** A stale Compose streaming Markdown buffer survived while the authoritative message history had completed.
- **Guard:** Reload persisted history only for inactive runtimes during `ResumeChat`, close stale channels, and reset Markdown state when the producer channel is replaced.

### 2026-09-20/21 — Bottom scroll lost after history restoration

- **Symptom:** The restored response appeared around the latest user message instead of at the bottom.
- **Root cause:** The message count did not change, so the existing item-count scroll effect did not run again.
- **Guard:** Increment `ChatUiState.scrollToLatestRequest` after restoration and use that token for the explicit bottom-anchor scroll.

### 2026-09-20/21 — Foreground completion flashed twice

- **Symptom:** A task completing while the app was visible caused two visible flashes.
- **Root cause:** Background restoration behavior was applied to ordinary completion: Markdown state was recreated on `partial` changes and the complete message list triggered an extra scroll effect.
- **Guard:** Key streaming state by channel identity only, keep normal completion on the existing channel, and reserve explicit restoration scrolling for `scrollToLatestRequest`.

## Future entries

Add new entries here using this format:

```text
### YYYY-MM-DD — Short symptom

- **Symptom:** What the user saw.
- **Root cause:** The lifecycle/state interaction that caused it.
- **Guard:** The invariant, test, or architectural boundary added to prevent recurrence.
```
