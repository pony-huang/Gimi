# Bottom sheet

[简体中文](../../zh/ui/bottom-sheet.md) · English

> The shared bottom-sheet components in `:core:designsystem`: the height model,
> the gesture rules, the slot skeleton, and the migration checklist for the sheets
> that already exist in the app.

## Why a shared component

The app had eight bottom sheets and each one grew its own height rule: a hard
90 % of the screen, a 380 dp list cap duplicated in two files, a 92 % page cap,
a 560 dp cap, a two-thirds cap, and three sheets with no cap at all. The last
group could grow to the full screen, and the half-expanded state could leave
the action row of a sheet outside the visible area — a bug that shipped once and
cost a full debugging round.

`GimiSheetBase` replaces that with one rule set. A sheet is a modal surface
whose height is explicit, whose content can never push it taller, and whose
only resize affordance is the drag handle at the top.

## The components

| Component | Role |
|---|---|
| `GimiSheetBase` | Modal shell, drag handle, continuous height, slot skeleton, insets |
| `GimiSheet` | Derived: title row + body + optional footer |
| `GimiSearchSheet` | Derived: optional title row + search row + body + optional footer |
| `GimiSheetOptionRow` / `GimiSheetSwitchRow` | Rows used inside a sheet (moved out of the old `GimiBottomSheet`) |

All three live in `core/designsystem/.../ui/components`. `GimiSheetBase` owns
the layout; the derived components only decide what goes into the title, search
and footer slots.

## Height model

`SheetHeight` is either a fraction of the window height or an absolute value:

```kotlin
GimiSheet(
    onDismissRequest = onDismiss,
    title = "播放音色",
    initialHeight = SheetHeight.Fraction(0.5f),
    maxHeight = SheetHeight.Fraction(0.9f),
) { /* body */ }
```

- **The sheet hugs its content.** `initialHeight` is the upper bound of the
  body, not a forced height: a sheet with three options is only as tall as those
  three options, and a sheet with a long list reaches the bound and scrolls
  inside it. The blank area under a short list is the bug this replaces.
- **The maximum is a hard cap.** `GimiSheetBase` clamps the maximum to 90 % of
  the window, so no caller can open a full-screen sheet. The clamp lives in
  `resolveSheetHeights` and is covered by `GimiSheetHeightTest`.
- **The chrome is reserved first.** The drag handle, the title row and the
  footer are measured and subtracted from the maximum before the body is laid
  out, so the footer can never be squeezed out by a greedy body. On the first
  frame the measured values are not available yet, so `ReservedChromeHeight`
  temporarily holds the body back; as soon as the real heights arrive they
  replace the reserved value, which never stays deducted from the body's bound.
- **Height is continuous, with no detents.** The handle can rest anywhere
  between zero and the bound, and it stays where it is released. Dragging the
  body down to zero dismisses the sheet. Opening the sheet again starts from
  the initial height again.
- **A sheet may set its own bound per page** (`height = …`). The value wins
  over the initial height, and the next change resets any manual drag.

## Gesture rules

- **Only the drag handle changes the height.** The handle is a 32 dp strip at
  the top of the sheet, so a finger anywhere on it drags the sheet.
- **Content scrolling never moves the sheet.** `GimiSheetBase` disables
  Material3's own drag and nested scroll (`sheetGesturesEnabled = false`), so
  a list inside a sheet scrolls the list, not the sheet. This is what keeps
  "the sheet resizes while you scroll" from happening.
- **The keyboard does not move the sheet either.** The IME is part of
  `safeDrawing`, so the content is padded above the keyboard and an input row
  at the bottom of the sheet stays visible.
- The sheet is dismissed by the scrim, the back button, or by dragging the
  handle all the way down until the body reaches zero.

## Slot skeleton

The skeleton mirrors the conversation-history drawer, so the two read as the
same family:

```text
┌─────────────────────────────┐
│           ═══════           │  drag handle (32dp strip)
│ ✕          Title        ⋯   │  title row: optional leading, centered title, trailing slot
│ 🔍  Search            取消   │  search row (search sheets only)
├─────────────────────────────┤
│                             │
│  body — scrolls on its own  │  heightIn(max), hugs its content
│                             │
├─────────────────────────────┤
│  footer (input, actions)     │  fixed at the bottom
└─────────────────────────────┘
```

The title row is centered like the conversation drawer: the title is centered
across the full row width, with an optional leading icon (back) at the start and
a trailing slot at the end that accepts either `GimiSheetTextAction` or
`GimiSheetIconAction`. Closing always uses the trailing slot, so a drawer reads
"back on the left, close or action on the right".

The row's horizontal inset is `GimiListDefaults.HorizontalPadding` (16 dp) — the
same gutter the list cards inside the sheet use — so the title row and the body
below share one vertical grid. The title reserves a symmetric safe area sized to
the wider of the two slots: a long title never collides with a button, and the
title stays centered when only one side has a slot.

Insets are the component's business: the sheet consumes `safeDrawing`, and
callers must not add `navigationBarsPadding()` or `imePadding()` themselves.

## Callers

| Sheet | Where | Initial / max |
|---|---|---|
| Playback voice | `feature/modelsettings` `VoicePickerSheet` | 90 % cap; the handle shrinks the body continuously and dismisses at zero |
| Realtime voice | `feature/modelsettings` | 90 % |
| Add-to-chat | `feature/chat` `ChatAddToChatSheet` | 45 % home page, 90 % list pages |
| Permission mode | `feature/chat` `ChatPermissionModeSheet` | 28 % |
| Chat model picker | `feature/chat` `ChatModelPickerSheet` | 60 % / 90 % |
| Realtime preset / details | `feature/chat` `RealtimeVoiceScreen` | 40–50 % / 90 % |
| A2UI detail sheet | `feature/chat` `ChatA2uiContainers` | 65 % / 90 % |

The assistant panel and the collapsed capsule in `feature/assistant` are **not**
migrated: they are `Popup`-hosted rather than modal sheets and carry their own
drag handle.

## Notes for the next change

- The sheet must never be allowed to reach the full screen height. Change
  `GimiSheetDefaults.MaxHeightFraction` only with a reason, and keep
  `GimiSheetHeightTest` green.
- Do not add `Modifier.clickable` to the handle area outside
  `GimiSheetDragHandle`; the drag gesture lives there.
- A sheet that scrolls must keep its scrollable child inside the body slot.
  The body gives the bound with `heightIn(max)`: use `Column + verticalScroll`
  for short lists so the sheet hugs its content — a `LazyColumn` under a bounded
  height fills the body. Long content scrolls inside the body; callers should not
  add a second height cap.
- `GimiSheetController.hide { }` is the way to run code after the exit
  animation — for example to commit a selection once the sheet is gone.

## Related

- [Brand styling](brand-styling.md) — the shared canvas colour and corner
  treatment.
- [Preview-first UI design review](preview-first-design-review.md) — how to
  preview a sheet change before shipping it.
