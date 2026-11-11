English · [简体中文](../../zh/ui/predictive-back.md)

# Predictive back support

Reviewed: 2026-11-11. Audience: agents and developers changing back navigation, gestures, or system animations.

This document records the predictive back baseline for this app, the gaps that
were found, and the decisions that keep those gaps out of the current change. It
is a behavior/compatibility document, not a UI design draft: no visual design
approval or Compose preview draft is required for the work described here. Apply
[Preview-first UI design review](preview-first-design-review.md) if a future
change actually customizes back animations or return interactions.

**Status: no runtime code was changed by this work.** All entries below are based
on inspected source, official AndroidX sources jars, and platform documentation.
Nothing was verified on a device — see [Verification](#verification).

## Why most of the platform guide does not apply

The platform setup guide splits into two unrelated navigation stacks:

- This app uses **Navigation 3** (`androidx.navigation3:navigation3-ui:1.0.0`,
  `NavDisplay` in `app/src/main/java/github/ponyhuang/gimi/navigation/AppNavigation.kt`),
  not Navigation Compose. There is no `NavHost`, no `navigation-compose`
  dependency, and no `popEnterTransition` / `popExitTransition` anywhere in the
  repository.
- The guide's "navigation-compose 2.8.0 or higher" requirement and its
  `NavHost(popExitTransition = ...)` example belong to Navigation Compose only.
  Official shared-element navigation docs state instead that **all versions of
  Navigation 3 support predictive back**.
- Navigation 3's equivalent is `NavDisplay.predictivePopTransitionSpec`, which is
  already set at `AppNavigation.kt:217`. It is currently byte-identical to
  `popTransitionSpec` and takes only `swipeEdge`, not a progress value; progress
  is driven internally by `SeekableTransition.seekTo(progress, previousScene)`
  from `NavigationEventTransitionState.InProgress.latestEvent.progress`.

Therefore no dependency upgrade, no `NavHost` migration, and no new pop
transition is part of this work.

## Verified baseline

| Platform requirement | Documented floor | This app | Status |
|---|---|---|---|
| AndroidX Activity | 1.6.0+ | `activity` / `activity-compose` **1.13.0** | Satisfied |
| Navigation | `navigation-compose` 2.8.0+ | Navigation 3 `navigation3-ui` **1.0.0** | Not applicable |
| Material3 | 1.3.0+ | BOM **2026.02.01** → `material3` **1.4.0** (official BOM POM) | Satisfied |
| `android:enableOnBackInvokedCallback` | opt-in for older targets | Not declared; `targetSdk = 37` | No change needed (see below) |
| SDK levels | Android 15+ for default system animations | `compileSdk` 37.1 / `targetSdk` 37 / `minSdk` 34 | Satisfied |
| Root Activity must not intercept back | — | `MainActivity` has no `onBackPressed`, `addCallback`, `OnBackInvokedCallback`, `BackHandler`, or `finish()` | Satisfied |

`app/src/main/AndroidManifest.xml` does not declare
`android:enableOnBackInvokedCallback` on either the `<application>` or any
`<activity>`. Official docs say the flag is unnecessary for apps targeting
Android 15+, and the Android 16 behavior changes make back-to-home, cross-task,
and cross-activity animations default for apps targeting API 36+. **The literal
default value of the manifest attribute itself is not explicitly documented**;
this is recorded as an assumption. If `targetSdk` is ever lowered, re-check it.
With `minSdk = 34`, the Android 13 developer-option test procedure in the guide
is out of scope entirely.

## What already works

- **back-to-home**: `NavDisplay` 1.0.0 registers
  `NavigationBackHandler(isBackEnabled = scene.previousEntries.isNotEmpty())`.
  Under `SinglePaneSceneStrategy`, `previousEntries = entries.dropLast(1)`, so
  this is equivalent to `backStack.size > 1`. At the root destination the handler
  is disabled and the platform input unregisters its `OnBackInvokedCallback`;
  the system then handles the gesture and plays the back-to-home animation.
  `goBack` (`AppNavigation.kt:109-111`) is a no-op at the root and is not called
  there, so it does not swallow the gesture.
- **In-app predictive pop**: `predictivePopTransitionSpec` is wired; the
  platform `OnBackAnimationCallback` (`onBackStarted` / `onBackProgressed` /
  `onBackInvoked` / `onBackCancelled`) feeds `navigationevent` 1.0.0 through
  `activity` 1.13.0's dispatcher bridge.
- **Back handlers**: the six `androidx.activity.compose.BackHandler` call sites
  use the supported `OnBackPressedDispatcher` path, which `activity` 1.13.0
  bridges to `NavigationEventHandler` / `OnBackInvokedCallback`. They are not
  "unmigrated" callbacks. None is progress-aware; that affects in-app motion
  quality, not the availability of system animations.
- **Activity graph**: seven activities (two in `:app`, five permission /
  translucent trampolines in `:data:agent`), none of which intercepts back.
  There is no `taskAffinity`, `documentLaunchMode`, or
  `FLAG_ACTIVITY_NEW_DOCUMENT` / `MULTIPLE_TASK` entry.

## Scope of the system animations

| Animation | Applies here | Treatment |
|---|---|---|
| back-to-home | Yes — root destination | Acceptance target |
| cross-activity | In principle (7 activities, no interception) | Recorded as "no interception, no change"; see known gap 4 |
| cross-task | **N/A** — no multi-task / document entry | Explicitly out of scope |
| in-app pop (predictive) | Yes — `NavDisplay` | Acceptance target |
| Android 13 developer option | Out of scope (`minSdk = 34`) | Not tested |

Device matrix: Android 14 (developer option **Predictive back animations** ON),
Android 15 (default), Android 16+ (default, and the `targetSdk 36+` behavior
change). Cross-task is marked N/A in the checklist rather than silently skipped.

## Decision record

Decisions locked in the 2026-11-11 design review. "Yes" in the review adopted
the recommendation shown here.

| # | Decision |
|---|---|
| Q1 | Reframe the task from "migrate per the guide" to "audit, close real gaps, verify, document"; no dependency bumps, no `NavHost`. |
| Q2 | Acceptance covers back-to-home + in-app pop + drawer; cross-activity recorded as no-change, cross-task N/A; devices 14 / 15 / 16 only. |
| Q3 | Pass `drawerState` to `ModalDrawerSheet` — **superseded by Q10 after source inspection; not applied**. |
| Q4 | Do not change `GimiSheetBase`; verify on device whether its configuration still animates, record the result. |
| Q5 | Do not migrate the six `BackHandler` call sites; audit only, with attention to the two unconditionally enabled ones. |
| Q6 | Treat the floating overlay `Key.Back` path as an independent compatibility regression, not an animation gap; include it, but verify first. |
| Q7 | Do not add the manifest flag; record the `targetSdk 37` rationale. |
| Q8 | Deliver bilingual specs, an `AGENTS.md` index row, a manual verification checklist, no automated UI tests, no preview draft/design approval. |
| Q9 | Realtime voice back semantics: **code wins**. Back ends the call and returns to text chat; align the two pipeline specs, change no runtime behavior. |
| Q10 | If a drawer back-handler conflict or a missing BottomSheet animation is found, do not fix the return interaction in place; revert/record as a known limitation and a separate motion task. |
| Q11 | Allow a minimal compatibility fix for the overlay only if device verification confirms the `Key.Back` path actually fails. |
| Q12 | Keep the verification checklist as a bilingual "Verification" section in these specs; no new openspec change; no hand-edited `CHANGELOG.md` (release-please). |

## Known gaps and why they are not fixed here

1. **Drawer has no predictive back animation.**
   `feature/chat/.../ChatConversationDrawer.kt:175` uses the `ModalDrawerSheet`
   overload without `drawerState`. Material3 1.4.0 documents that this overload
   does not handle back and does not animate on Android 14+; the `drawerState`
   overload does both via `DrawerPredictiveBackHandler`.
   **Why not fixed:** that overload registers
   `PredictiveBackHandler(enabled = drawerState.isOpen)` inside `drawerContent`,
   which is composed *after* `ChatRoute.kt:146`'s `BackHandler`. Handlers are
   consumed LIFO (most recently composed enabled handler first), so in the
   overlapping state "drawer open + recent conversations multi-select + not
   deleting", back would **close the drawer instead of finishing the selection**.
   Selection would only end indirectly through
   `ChatRoute.kt:149`'s `LaunchedEffect(drawerState.currentValue == Closed)`.
   `ModalNavigationDrawer` itself registers no back handler, so there is no
   third competitor. Per Q10 this is a behavior change: the one-line fix is
   reverted, and the precedence design (reordering or narrowing a handler) is
   a separate task requiring preview-first motion review.

2. **Bottom sheet animation unverified.**
   `core/designsystem/.../GimiSheetBase.kt` sets
   `sheetGesturesEnabled = false`, `skipPartiallyExpanded = true`, and drives
   dismissal through a custom `GimiSheetController`. `ModalBottomSheet` animates
   with predictive back by default, but this exact configuration was not
   verified on a device. Per Q4/Q10: no change, record the device result.

3. **Floating overlay back may break on Android 16.**
   `feature/assistant/.../AssistantConversationPanel.kt:94-101` consumes
   `Key.Back` through `onPreviewKeyEvent` in `OVERLAY` mode, with a comment
   explaining that the overlay host has no `OnBackPressedDispatcher`. Android 16
   behavior changes for apps targeting API 36+ state that `onBackPressed` is not
   called and `KeyEvent.KEYCODE_BACK` is not dispatched anymore. This is a
   **functional regression** (the panel can no longer be collapsed), not an
   animation gap. Q11 permits a minimal compatibility fix, but only after device
   verification; **no device verification was performed**, so no code was
   changed. This is a P0 checklist item.

4. **Compose back handlers and cross-activity animation.**
   The platform note says intercepting back at an Activity disables the
   cross-activity animation. No Activity here intercepts back directly, but the
   six Compose `BackHandler`s register on the host Activity's dispatcher.
   Whether that counts as Activity-level interception cannot be settled from
   static source; it is listed for device verification (V6).

5. **`predictivePopTransitionSpec` is identical to `popTransitionSpec`.**
   No edge- or progress-specific differentiation. Accepted as-is; customizing it
   would be a motion design change under the preview-first workflow.

6. **Voice specs only partly aligned.**
   `spec/en/voice/pipeline.md` and `spec/zh/voice/pipeline.md` now describe the
   code's behavior (back = end call + return to text chat; the in-page hang-up
   button only ends the session). These two companions are the full scope of Q9.
   `spec/en/voice/realtime-voice-system.md:80` /
   `spec/zh/voice/realtime-voice-system.md:63` still say resources are released
   only on an explicit "End call", and `spec/en/voice/runtime.md:157-159` /
   `spec/zh/voice/runtime.md:129-130` list only two end-call entry points. Those
   four spots remain a documented follow-up; they were not rewritten because Q9
   authorized only the pipeline companions.

7. **No automated coverage.**
   There are no existing back-navigation assertions in `src/test` or
   `src/androidTest`, and `AGENTS.md` forbids new automated UI interaction,
   layout, or screenshot tests. Verification is manual and recording-based.

## Verification

**Pending — no device run has been performed.** Per
[Preview-first UI design review](preview-first-design-review.md), motion may only
be reported as verified after actually operating it or reviewing a recording.
Capture a screen recording for every animation item below; record the device,
OS build, and whether the developer option was enabled. If an item fails, follow
the decision linked in the last column — do not improvise an in-place fix.

| # | Device / setup | Steps | Expected | Evidence / on failure |
|---|---|---|---|---|
| V1 | Android 14 (API 34), Developer options → **Predictive back animations** ON | From the root chat screen, slowly swipe back, then cancel; repeat and commit | App scales/translates toward home following the gesture; cancel restores the same screen; commit returns to launcher | Recording. On failure: Q1/Q2 — reopen the root-handler audit |
| V2 | Android 15 (API 35), no developer option | Same as V1 | Same back-to-home animation, enabled by default | Recording |
| V3 | Android 16+ (API 36+), `targetSdk 37` | Same as V1 | Same back-to-home animation under the API 36+ default | Recording |
| V4 | V1–V3 | From a non-root `NavEntry` (e.g. Settings), slowly swipe back, cancel, then commit | `predictivePopTransitionSpec` slide tracks the gesture; cancel leaves the screen unchanged; commit pops exactly one entry | Recording. Q2 acceptance target |
| V5 | V1–V3 | Open the chat drawer, press back; then open the drawer, enter recent-conversation multi-select, press back | First case: record the current baseline (no drawer back handler). Second case: **must finish selection and keep the drawer open** | Recording. P0 regression guard for gap 1; if it regresses after any future `drawerState` change, Q10 says revert |
| V6 | V1–V3 | From a `:data:agent` permission / translucent Activity, press back | Cross-activity animation plays, returns to `MainActivity` | Recording; resolves gap 4. Mark N/A if no such entry is reachable on the device |
| V7 | V1–V3 | Any multi-task / document entry | None exists | Record **N/A — no cross-task trigger** (Q2); do not silently skip |
| V8 | V1–V3 | Open any `GimiSheetBase`-based sheet, slowly swipe back | Determine whether the predictive back animation plays with `sheetGesturesEnabled = false` | Recording; record the conclusion for gap 2, no code change (Q4/Q10) |
| V9 | Android 16+ only | Enter `OVERLAY` assistant mode, press back | Panel collapses | **P0.** If it does not collapse, Q11 permits a minimal compatibility fix that preserves the existing interaction model; otherwise change no code |
| V10 | V1–V3 | In the realtime voice page, press back; separately tap the in-page hang-up button | Back ends the call and returns to text chat; hang-up only ends the session and keeps the voice page in the idle state | Behavior check for Q9; confirms the spec alignment |
| V11 | Android 14 only | Confirm the developer option is actually enabled before V1 | Predictive back animations toggle is ON | Screenshot; without it V1 results are invalid |

Android 13 (API 33) is intentionally absent: `minSdk = 34`.

## Change log

- 2026-11-11 — Baseline audited against source, AndroidX 1.0.0/1.4.0/1.13.0
  sources jars, the Compose BOM POM, and platform docs. Decisions Q1–Q12 locked.
  `spec/en/voice/pipeline.md` + `spec/zh/voice/pipeline.md` aligned to the
  realtime voice back behavior; `AGENTS.md` index row added; this document and
  its Chinese companion created. **No runtime source changed.**
