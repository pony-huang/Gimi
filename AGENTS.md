# Repository Guidelines

## Structure, Ownership, and Architecture

### Module map

`settings.gradle.kts` is the source of truth for this multi-module Android app. Responsibilities:

- `:app` — composition root for startup, top-level navigation, Android services, and cross-capability Hilt wiring; never a catch-all for UI, domain, data, or provider logic.
- `:core:common`, `:core:audio`, `:core:network`, `:core:testing` — narrow shared infrastructure.
- `:core:designsystem` — theme tokens and stateless, business-agnostic Compose components.
- `:domain:<capability>` — domain models, repository interfaces, and use cases.
- `:data:<capability>` — repository implementations, Room/Preferences/Keystore storage, Android gateways, network clients, and third-party SDK adapters.
- `:feature:<capability>` — feature UI, routes, contracts, ViewModels, and feature-specific components.
- `gradle/libs.versions.toml` — dependency/version catalog. Project working notes and temporary artifacts live under ignored `temp/`; long-lived documentation belongs in `spec/en/` with a Chinese companion in `spec/zh/`.

### Capabilities

Current domain capabilities are `modelcatalog`, `assistant`, `conversation`, `voice`, `mcp`, `workfiles`, `permissions`, `toolauthorization`, `skills`, `appupdate`, `plugin`, `recommendation`, `memory`, `workspace`, and `appearance`. Provider/runtime-only data capabilities also include `agent`. Current features are `modelsettings`, `settings`, `mcp`, `workfiles`, `permissions`, `toolauthorization`, `skills`, `chat`, `assistant`, `plugin`, `recommendation`, `memory`, and `workspace`.

### Ownership and placement

Module ownership overrides legacy package names. Use packages matching the owning capability; never recreate catch-all trees such as `app/ui`, `app/data`, or `app/model`. Place new work as follows:

1. Feature-only presentation or interaction state → `:feature:<capability>`.
2. Business model, rule, repository contract, or reusable workflow → `:domain:<capability>`.
3. Persistence, network, Android API, secure storage, or SDK adapter → `:data:<capability>`.
4. Stable, business-agnostic shared infrastructure or UI with at least two real consumers → the narrowest `:core:*`.
5. Startup, navigation graph, service registration, or cross-capability composition → `:app`.

If no capability owns the change, create a focused domain/data/feature slice; do not use a nearby unrelated module or create empty placeholder modules.

### Dependency directions

Required runtime and dependency directions:

```text
Route -> Stateless Screen
  |          ^
  v          |
ViewModel <- UiAction
  |
  v
UseCase / Domain Repository Interface
  ^
  |
Data Repository Implementation -> Room / Secure Settings / Remote Gateway

:app -> :feature:* + :data:* + required domain/core modules
:feature:* -> :domain:* + :core:designsystem
:data:* -> :domain:* + required core infrastructure
:domain:* -> Kotlin/coroutines and explicitly shared domain contracts only
feature A -X-> feature B
domain/data/core -X-> feature or app
```

### Mandatory boundaries

- Feature modules never depend on one another. Move shared business contracts to domain and genuinely business-agnostic UI to `:core:designsystem`.
- A feature may depend on another narrow core module (e.g. `:core:common`, `:core:audio`) only when the dependency is business-agnostic and explicitly documented; otherwise expose a domain contract.
- Feature modules never depend on `:app`; expose contracts from the owning domain or core module and perform composition in `:app`.
- Domain code never depends on Android, Compose, Hilt, Room, OkHttp, provider SDKs, or data models; domain repositories are interfaces.
- Keep SDK, HTTP, database, Keystore, Preferences, and Android framework details behind data/core gateways or repository implementations.
- ViewModels depend on use cases or domain repository interfaces, never concrete repositories, DAOs, Context, Toast, navigation controllers, OkHttp, Room, or provider SDK objects.
- Use a use case for branching business rules, cross-repository operations, or reusable workflows; avoid trivial pass-through use cases unless they establish a necessary contract boundary.
- Prefer constructor injection and Hilt. Bind domain interfaces in the owning data module; compose capabilities in `:app`.
- Avoid generic `Manager`, `Helper`, `Utils`, or `BaseViewModel` dumping grounds. Name one responsibility and use the narrowest owner.
- Suspending code should let cancellation propagate naturally: avoid broad catches, catch recoverable exception types, and use the shared cancellation-aware recovery helper only at boundaries that require a fallback.
- Do not promote code to `core` merely because two files look alike.
- Resolve graph conflicts through contracts/interfaces, not shortcut dependencies. Document any temporary exception with its owner and removal condition.

## Compose, UI, i18n, and Style

### UI and state flow

- Each screen exposes one immutable `*UiState`; user intent enters through a `*Action`/`*UiAction` contract or explicit synchronous callbacks.
- Route composables collect state lifecycle-aware and own navigation, Toast/Snackbar delivery, activity results, permissions, URL opening, and other Android/UI side effects.
- Screens and business components are stateless: state enters as parameters and events leave as callbacks. They do not fetch ViewModels, repositories, or Context, or launch business coroutines.
- Keep loading, testing, refreshing, notices, and other ephemeral business-operation state in the ViewModel.
- `:core:designsystem` components reference no ViewModels, repositories, domain models, navigation, Toast, network calls, or business coroutines; keep them independently previewable with fixture data. Accepted exception: `ui/settings/llmmodel/LLMModelServiceIcon` and its `ic_model_provider_*` drawables are shared brand assets consumed by both `:feature:chat` and `:feature:modelsettings`; they stay in `:core:designsystem` because feature-to-feature dependencies are forbidden. Second exception: `ui/components/VoicePickerPage` and `ui/components/VoicePickerDialog` (with the `voice_picker_*`/`voice_gender_*`/`voice_group_*` strings and the `:domain:voice` api dependency) are the two presentations of the shared voice picker: the in-drawer page is consumed by `:feature:chat`, the dialog by `:feature:modelsettings`. Both stay in `:core:designsystem` so the shared copy and domain types live in one place without a feature-to-feature dependency.

### Preview-first UI design review

Before changing UI, read [Preview-first UI design review](spec/en/ui/preview-first-design-review.md) for the design workflow, interaction continuity, and motion verification principles. Small, localized adjustments may proceed directly; larger changes require an isolated Compose preview draft, an image-delivery preference, and explicit design approval before implementation unless the user explicitly waives approval. Preserve relevant production previews and use proportional verification.

### Brand styling

Before designing UI or creating Gimi-branded visual artifacts, read [Gimi brand styling](spec/en/ui/brand-styling.md). Use the current blue-and-neutral theme, typography, component shapes, and semantic color roles as the visual baseline.

### Theming and insets

- Preserve existing theme tokens, dimensions, copy, accessibility semantics, and insets unless explicitly changing them. Use Material 3/design-system tokens instead of hard-coded UI colors.
- Edge-to-edge UI must keep controls and primary content clear of status bars, cutouts, navigation bars, and gesture areas using appropriate insets such as `statusBarsPadding`, `navigationBarsPadding`, or `safeDrawingPadding`; verify visually.

### i18n

- Default locale is Chinese (`values/`); English is in `values-en/`. Each module owns both `strings.xml` files; shared business-agnostic copy belongs in `:core:designsystem`.
- All user-facing `Text`, content descriptions, dialog/field copy, and surfaced Toasts must use `stringResource(R.string.xxx)`. Doc comments and `@Preview` fixtures are exempt.

### Code style

- Use idiomatic Kotlin, four-space indentation, nearby trailing-comma style, PascalCase for types/composables/type files, and camelCase for functions/properties/locals. Match nearby formatting and keep imports organized; no formatter or linter is configured.
- Add concise Chinese comments to complex logic, explaining business invariants, lifecycle/call timing, and why the implementation is necessary rather than restating individual lines.
- Every Kotlin `data class` and `data object` must have KDoc describing its responsibility and the meaning of its data. Document non-obvious properties with `@property` entries or focused property comments.

### Compose A2UI (agentic UI) reference

A2UI (agentic UI) lets the LLM agent produce interactive native Compose UI by emitting JSON; the component catalog is the contract, and agent JSON is untrusted input validated by `a2ui-engine` before it reaches UI state. Before adding or changing A2UI generation, validation, catalog components, or upgrading the androidx a2ui artifacts, read [A2UI business execution](spec/en/a2ui/a2ui-execution.md) — it owns the protocol background, canonical reference links, the end-to-end on-device pipeline, and the upgrade checklist.

## Build and Verification

### Build commands

Run from the repository root with `gradlew.bat` on Windows:

- `.\gradlew.bat app:compileDebugKotlin` — compile the complete debug graph.
- `.\gradlew.bat :feature:<name>:testDebugUnitTest` — run affected feature JVM tests; use the equivalent domain/data task as needed.
- `.\gradlew.bat app:testDebugUnitTest` — run app JVM tests.
- `.\gradlew.bat app:assembleDebug` — build debug APKs.
- `.\gradlew.bat connectedDebugAndroidTest` — run configured instrumentation tests.
- `.\gradlew.bat check` — run configured verification lifecycle tasks.
- `.\gradlew.bat clean` — remove generated build output.

Android Studio may run the `app` debug configuration on a device or emulator.

### Fast validation

- For JVM tests and required Gradle checks, choose the smallest targets satisfying the testing rules below and combine compatible targets in one `gradlew.bat` invocation when practical. When a change requires both JVM tests and APK assembly, finish the focused tests and resolve their failures before starting assembly; combine compatible compile/test targets within that stage.
- Changes to Hilt/Room annotations, generated code, or bytecode transforms need the Gradle build required by the testing rules. Keep one build running at a time.
- Validate a coherent source state: finish or explicitly stop an active build before further edits, and associate each result with the source state it actually checked. Validate subsequent changes separately.
- After switching branches, a generated-code failure referencing a removed class may come from stale outputs. Check the current source/dependency graph and generated-file timestamps, then clean only the affected module's generated outputs when confirmed. Use a repository-wide `clean` only when evidence shows the problem spans modules.
- After a test failure, inspect the exact failing test and cause before rerunning. Repair a stale fixture or source issue, then rerun the same targeted tests; report unrelated failures separately. Reuse successful validation for behavior unchanged by subsequent edits. Record compilation, tests, APK assembly, deployment, and device verification separately; distinguish active build time from IDE/dialog waits and infrastructure retries. A combined test-and-assembly command's elapsed time is not the unit-test execution time.

### CI/CD (GitHub Actions, `.github/workflows/`)

- Development branch pushes run CI verification; release automation maintains version PRs and publishes signed App/plugin APKs.
- Read [Development, verification, and release](spec/en/releasing.md) before changing workflows or release wiring, reviewing version PRs, or handling RCs and release retries. Keep detailed CI/CD behavior in that spec; workflow files are the source of truth.

### Testing rules

Choose the smallest validation that covers the changed behavior and risk. Review a coherent change before running checks; escalate when focused results expose uncertainty.

| Change | Default validation |
|---|---|
| Text-only documentation, comments, copy, or prompts | Review the diff and run `git diff --check`; no build or automated tests unless logic, structured contracts, resource wiring, or UI behavior also changes. |
| Presentation-only Compose UI | Relevant `@Preview` states and affected-module compilation; manual device checks when needed. Do not add automated UI interaction, layout, or screenshot tests. |
| Business behavior or a behavioral bug fix | Focused behavior tests in the owning layer and affected-module compilation. Core rules require domain/use-case or ViewModel coverage; repositories/gateways need applicable success, failure, exception, and mapping coverage. |
| Behavior-preserving refactor of core ViewModel transitions or externally visible repository behavior | Characterization coverage and affected-module compilation. |
| Public module contracts or app composition | `app:compileDebugKotlin` and focused tests for affected behavior. |
| DI/generated wiring, resource identifiers or wiring, manifest, build configuration, packaging, or deployment | Required focused tests first, then `app:assembleDebug`. |

- After code changes, run `git diff --check` and compile affected modules. A higher-level build satisfies compilation it actually covers; do not repeat covered compile targets. Every module must declare its own dependencies.
- Use TDD selectively for core business logic, complex problems with a meaningful test seam, or an explicit user request. Simple UI, copy, and low-risk wiring do not require TDD. This scope takes precedence over blanket TDD skill requirements.
- Keep the smallest useful regression tests protecting business behavior, external contracts, risk boundaries, or fixed defects; preserve existing meaningful coverage. Remove temporary diagnostic tests before committing or move reusable scratch work to ignored `temp/`. Judge tests by the independent behavior and failures they protect, not assertion length or whether expected values are literals.
- Use JUnit 4 in the owning module's `src/test`, with behavior-based `*Test.kt` names; Android-specific integration tests belong in `src/androidTest`. Tests must run on Windows and Ubuntu with isolated fixtures, fakes or mocked gateways, and no live model APIs or real credentials.
- Keep Compose business rules and permission/credential decisions in their owning tested layers. Retain previews for relevant visual and conditional states.
- Full module/app suites are for broad refactors, integration milestones, pre-release checks, CI, or an explicit request. Use device checks for hardware, permissions, system integration, and UI behavior that JVM tests cannot establish.
- Inspect the exact cause before retrying failures. Stop when the same infrastructure cause repeats without new evidence supporting another retry; report passed and incomplete checks and hand off remaining verification. Compilation success does not replace required tests or device checks.
- Read [Testing and verification](spec/en/testing.md) when writing or evaluating tests, diagnosing cross-platform failures, or changing HTTP/paid integrations. It owns detailed fixture, boundary, diagnostic, and test-retention guidance. Report compilation, tests, assembly, deployment, and device verification separately.

## Refactoring, Git, and Security

### Refactoring

- Refactor one closed capability at a time; preserve behavior and keep logical commits independently compilable and revertible.
- Use `git mv` for moves. Move-only commits change only paths, packages, imports, and necessary visibility. Inspect `git diff --find-renames --summary`, compile affected modules, and remove empty legacy package directories.
- This project is in the development and exploration stage. Do not preserve old databases, serialized payloads, configuration fields, APIs, or compatibility adapters unless the user explicitly requests it; choose the cleanest current design, allow destructive local-data recreation, and do not add historical migrations.
- Do not leave forwarding wrappers, duplicate implementations, or legacy adapters without an explicit current requirement.
- Avoid drive-by package moves or broad unrelated cleanup.

### Git

- When the user requests a commit, format its subject as `<type>[optional scope][!]: <description>`. Use `feat` for features, `fix` for bug fixes, and an appropriate type such as `docs`, `refactor`, `test`, `build`, `ci`, or `chore` for other changes. Mark breaking changes with `!` or a `BREAKING CHANGE:` footer; separate any optional body or footer from the subject with a blank line. This rule is self-contained; do not look up an external commit convention unless the user explicitly asks.
- Keep commits focused with imperative subjects under 72 characters. Pull requests describe user-visible changes, tests, relevant issue/OpenSpec change, and Compose screenshots.
- Before committing, inspect staged paths, run `git diff --cached --check`, exclude generated/local artifacts, and confirm architecture boundaries.

### Security

- Never commit build outputs, caches, generated reports, IDE state, API keys, tokens, or credentials. Store local secrets in ignored `local.properties` or environment variables and revoke exposed credentials.
- Filter build/IDE logs before emitting them to tool output or conversation. Prefer an allowlist of task progress, compiler diagnostics, and terminal build results. Exclude environment dumps and redact credentials before output, rather than printing raw log tails and filtering afterward.
- Treat untracked `app/release/` APKs as local artifacts; include or delete them only when explicitly requested.

## Working Files and Caches

### The `temp/` directory

- Place any temporary working files, scratch artifacts, downloaded assets, or intermediate caches in the repository-root `temp/` directory (e.g. `temp/<purpose>/...`), not in scattered locations inside modules or the repo root.
- `temp/` is git-ignored; treat its contents as ephemeral and safe to delete at any time. Do not commit anything under `temp/`.
- When a tool needs a default cache directory (Gradle, IDE, SDK downloads, etc.), redirect it to a subdirectory of `temp/` or keep it under an already-ignored top-level path such as `.gradle`, `.idea`, or `.kotlin`; do not introduce new top-level cache directories.

### Worktree sharing

- `temp/` is shared across git worktrees: Claude Code's native `worktree.symlinkDirectories` setting (`.claude/settings.json`, set to `["temp"]`) symlinks the main checkout's `temp/` into every worktree it creates (`claude --worktree` and `EnterWorktree`). Put reusable reference resources (downloaded docs, extracted SDK API sources, vendor pins) in `temp/` so every worktree sees them without re-downloading. Because `temp/` is a link to a shared target, never `rm -rf` it from a worktree (that would recurse into the shared directory); remove only the link with `rmdir`/`os.rmdir`, or run `git worktree remove --force` after deleting the link.

## Android Tooling and Physical Device Verification

### Tool preference

- Prefer the `android` CLI for project, deployment, launch, screenshot, layout, and emulator workflows; consult `android help <command>` first.
- Use plain `android run` for deployment/launch, `android layout` for primary UI inspection, `android layout --diff` for focused changes, and `android screen capture` for secondary visual checks. Always inspect captured PNGs visually.
- Do not pass `--debug` during routine physical-device installation, launch, UI, layout, screenshot, or interaction verification. On some devices it enables "Waiting For Debugger", preventing the Activity and Compose hierarchy from appearing. Use `android run --debug` only when the task explicitly requires attaching a debugger; otherwise launch normally before inspecting the UI.
- Query connected devices/emulators with `adb devices -l` directly; device discovery is adb-first. The `android` CLI exposes no device-list command and cannot resolve some wireless serials (e.g. `adb-tls` mDNS names). Always resolve the serial dynamically at time of use; never hard-code or reuse a serial from memory.
- For everything other than device discovery, prefer the `android` CLI. Fall back to `adb` only when the installed CLI is unavailable, fails, or lacks the operation, such as wake, raw input, install/launch/screencap on a serial the CLI cannot resolve, or a narrow shell command. Record the reason, limit ADB to that operation, then return to the CLI.

### Device verification

- Prefer a connected physical Android device for installation, UI, permissions, system insets, and hardware behavior. Never hard-code a manufacturer, model, or serial; resolve the current physical device dynamically with `adb devices -l`. Use an emulator only when no suitable device exists or the task requires one, and report the fallback.
- Build and run on the selected physical device with:

```powershell
.\gradlew.bat app:assembleDebug
android run --device "$serial" --apks=app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
```

- If `android run`/`android screen capture` cannot resolve the serial (e.g. wireless `adb-tls` names), fall back to `adb -s "$serial" install -r <apk>`, launch with `adb -s "$serial" shell monkey -p <applicationId> -c android.intent.category.LAUNCHER 1`, and capture with `adb -s "$serial" exec-out screencap -p > screen.png`; record the fallback reason.
- Use `android layout --device "$serial" --pretty` for hierarchy/text/bounds and `android screen capture -o build\device-screen.png` for visuals. When the CLI cannot resolve the device, `adb -s "$serial" shell uiautomator dump` + `cat` is the hierarchy fallback. Use narrowly scoped `adb -s "$serial" shell input ...` only when CLI lacks required input.
- If the device is asleep or not foregrounded and CLI has no wake command, use only `adb -s "$serial" shell input keyevent KEYCODE_WAKEUP`, then re-check with `android layout` or `android screen capture`. Never bypass device security. Report persistent test changes such as model selection.

## Spec documentation

Long-lived spec documents live in `spec/en/` (English, read by agents) with Chinese companions under `spec/zh/` at the same relative path. Each document opens with a cross-language link (and, for dated references, a `Reviewed:` date and intended audience). When a business area gains a new spec document, add a row to the table below; keep the English and Chinese companions in sync when updating a spec.

Load specs on demand:

- Use the table's triggers to select documents before changing a documented area; leave unrelated specs unloaded. Read English by default; load the Chinese companion when editing or checking the translation.
- Start with headings and read the relevant sections, including their constraints, known gaps, and required checks. Follow links only when needed for the task or explicitly required by the document; expand the reading scope when the change crosses areas.
- Base implementation claims on inspected code and spec text. Treat unread content as unknown; when evidence is missing or conflicts, inspect the relevant source and state any unresolved uncertainty instead of inventing behavior.

| Document (English) | Read before… |
|---|---|
| [Preview-first UI design review](spec/en/ui/preview-first-design-review.md) | changing UI or motion, deciding whether a design draft is required, or preparing design approval and image delivery |
| [Gimi brand styling](spec/en/ui/brand-styling.md) | designing UI or creating Gimi-branded visual artifacts; choosing colors, typography, shapes, or brand assets |
| [Predictive back support](spec/en/ui/predictive-back.md) | changing back navigation, system animations, drawer/bottom-sheet back handling, or verifying predictive back on a device |
| [Agent business execution](spec/en/agent/agent-execution.md) | adding an `AgentContribution`, changing run lifecycle, confirmation, event pipeline, or cache-key behavior |
| [A2UI business execution](spec/en/a2ui/a2ui-execution.md) | changing the `generate_a2ui` chain, protocol validation, chat catalog components, or upgrading a2ui artifacts |
| [AppFunctions](spec/en/appfunctions.md) | adding or changing app function tools exposed to the agent |
| [Realtime voice system](spec/en/voice/realtime-voice-system.md) | touching realtime voice; read its "Known gaps" section first |
| [Realtime voice pipeline](spec/en/voice/pipeline.md) | changing capture, echo cancellation, VAD/segmentation, recognition, generation, synthesis/playback, or barge-in stage mechanics |
| [Realtime voice state, devices, and lifecycle](spec/en/voice/runtime.md) | changing the voice state machine, audio device routing, foreground service, or timeout/retry/cancellation semantics |
| [Realtime voice test coverage](spec/en/voice/testing.md) | writing, moving, or deleting realtime voice tests, or judging what only device verification can cover |
| [Plugins](spec/en/plugin/plugins.md) | adding or changing a plugin, or its integrated platform |
| [Testing and verification](spec/en/testing.md) | writing or evaluating tests, diagnosing cross-platform failures, changing HTTP/paid integrations |
| [Releasing](spec/en/releasing.md) | changing CI/CD workflows or release wiring, version PRs, prereleases, tag-based releases |
