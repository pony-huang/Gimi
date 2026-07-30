# Repository Guidelines

## Structure, Ownership, and Architecture

`settings.gradle.kts` is the source of truth for this multi-module Android app:

- `:app`: composition root for startup, top-level navigation, Android services/app functions, and cross-capability Hilt wiring; never a catch-all for UI, domain, data, or provider logic.
- `:core:common`, `:core:audio`, `:core:network`, `:core:testing`: narrow shared infrastructure.
- `:core:designsystem`: theme tokens and stateless, business-agnostic Compose components.
- `:domain:<capability>`: domain models, repository interfaces, and use cases.
- `:data:<capability>`: repository implementations, Room/Preferences/Keystore storage, Android gateways, network clients, and third-party SDK adapters.
- `:feature:<capability>`: feature UI, routes, contracts, ViewModels, and feature-specific components.
- `gradle/libs.versions.toml`: dependency/version catalog; project working notes and temporary artifacts live under ignored `temp/`. Long-lived documentation belongs in `docs/` only after that directory is introduced.

Current domain capabilities are `modelcatalog`, `assistant`, `conversation`, `speech`, `mcp`, `workfiles`, `permissions`, `toolauthorization`, and `skills`. Provider/runtime-only data capabilities also include `agent` and `voicewake`. Current features are `modelsettings`, `settings`, `mcp`, `workfiles`, `permissions`, `toolauthorization`, `voicewake`, `skills`, and `chat`.

Module ownership overrides legacy package names. Use packages matching the owning capability; never recreate catch-all trees such as `app/ui`, `app/data`, or `app/model`. Place new work as follows:

1. Feature-only presentation or interaction state -> `:feature:<capability>`.
2. Business model, rule, repository contract, or reusable workflow -> `:domain:<capability>`.
3. Persistence, network, Android API, secure storage, or SDK adapter -> `:data:<capability>`.
4. Stable, business-agnostic shared infrastructure or UI with at least two real consumers -> the narrowest `:core:*`.
5. Startup, navigation graph, service registration, or cross-capability composition -> `:app`.

If no capability owns the change, create a focused domain/data/feature slice; do not use a nearby unrelated module or create empty placeholder modules.

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

Mandatory boundaries:

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

- Each screen exposes one immutable `*UiState`; user intent enters through a `*Action`/`*UiAction` contract or explicit synchronous callbacks.
- Route composables collect state lifecycle-aware and own navigation, Toast/Snackbar delivery, activity results, permissions, URL opening, and other Android/UI side effects.
- Screens and business components are stateless: state enters as parameters and events leave as callbacks. They do not fetch ViewModels, repositories, or Context, or launch business coroutines.
- Keep loading, testing, refreshing, notices, and other ephemeral business-operation state in the ViewModel.
- `:core:designsystem` components reference no ViewModels, repositories, domain models, navigation, Toast, network calls, or business coroutines; keep them independently previewable and Compose-testable. Accepted exception: `ui/settings/llmmodel/LLMModelServiceIcon` and its `ic_model_provider_*` drawables are shared brand assets consumed by both `:feature:chat` and `:feature:modelsettings`; they stay in `:core:designsystem` because feature-to-feature dependencies are forbidden.
- Preserve existing theme tokens, dimensions, copy, accessibility semantics, and insets unless explicitly changing them. Use Material 3/design-system tokens instead of hard-coded UI colors.
- Edge-to-edge UI must keep controls and primary content clear of status bars, cutouts, navigation bars, and gesture areas using appropriate insets such as `statusBarsPadding`, `navigationBarsPadding`, or `safeDrawingPadding`; verify visually.
- Default locale is Chinese (`values/`); English is in `values-en/`. Each module owns both `strings.xml` files; shared business-agnostic copy belongs in `:core:designsystem`.
- All user-facing `Text`, content descriptions, dialog/field copy, and surfaced Toasts must use `stringResource(R.string.xxx)`. Doc comments and `@Preview` fixtures are exempt.
- Use idiomatic Kotlin, four-space indentation, nearby trailing-comma style, PascalCase for types/composables/type files, and camelCase for functions/properties/locals. Match nearby formatting and keep imports organized; no formatter or linter is configured.
- Add concise Chinese comments to complex logic, explaining business invariants, lifecycle/call timing, and why the implementation is necessary rather than restating individual lines.
- Every Kotlin `data class` and `data object` must have KDoc describing its responsibility and the meaning of its data. Document non-obvious properties with `@property` entries or focused property comments.

## Build and Verification

Run from the repository root with `gradlew.bat` on Windows:

- `.\gradlew.bat app:compileDebugKotlin`: compile the complete debug graph.
- `.\gradlew.bat :feature:<name>:testDebugUnitTest`: run affected feature JVM tests; use the equivalent domain/data task as needed.
- `.\gradlew.bat app:testDebugUnitTest`: run app JVM tests.
- `.\gradlew.bat app:assembleDebug`: build debug APKs.
- `.\gradlew.bat connectedDebugAndroidTest`: run configured instrumentation tests.
- `.\gradlew.bat check`: run configured verification lifecycle tasks.
- `.\gradlew.bat clean`: remove generated build output.

Android Studio may run the `app` debug configuration on a device or emulator.

GitHub Actions (`.github/workflows/`):

- `ci.yml`: push/PR to `main` runs `testDebugUnitTest`, `assembleDebug`, and `app:lintDebug` on JDK 21 (Ubuntu).
- `release.yml` (`publish-android-release`): triggered by pushing a `v*` tag or by manual `workflow_dispatch` (empty version input falls back to the `versionName` default in `app/build.gradle.kts`). Builds `app:assembleRelease` with `-PreleaseVersionName`/`-PreleaseVersionCode` injected and attaches only the APKs to a GitHub Release (the R8 mapping file is not published). Signing priority: CI `-P` properties > IDE Generate Signed APK wizard injection (`android.injected.signing.*`) > debug fallback (see `app/build.gradle.kts`); CI official signing activates only when the `RELEASE_KEYSTORE_BASE64`/`RELEASE_KEYSTORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` secrets are configured. No Google Play publishing.

Testing rules:

- Use JUnit 4 in the owning module's `src/test`; use behavior-based `*Test.kt` names. Put Android/Compose interaction coverage in `src/androidTest`.
- Add characterization tests before behavior-preserving refactors of ViewModel transitions or externally visible repository behavior.
- Changed business rules require domain/use-case or ViewModel tests. Gateways/repositories require applicable success, failure, exception, and mapping coverage.
- Meaningfully interactive or conditional stateless Compose components require UI coverage when practical. Pure visual-only changes may use compilation plus screenshot/device verification when behavior is unchanged.
- Tests use fakes, MockWebServer, or mocked gateways, never live model APIs or real credentials.
- At minimum, compile the affected module and `app:compileDebugKotlin`, run affected JVM tests, and run `git diff --check`. Also run `app:assembleDebug` for wiring, resource, manifest, or packaging changes.
- Every module declares and verifies its own dependencies; compilation through an undeclared `:app` transitive dependency is not completion.
- After repeated runner/device/infrastructure failures following a successful compile, stop retrying, hand off to manual verification, and record the successful compile, repeated failure, and any manual checks.

## Refactoring, Git, and Security

- Refactor one closed capability at a time; preserve behavior and keep logical commits independently compilable and revertible.
- Use IDE Refactor Move or `git mv`. Move-only commits change only paths, packages, imports, and necessary visibility. Inspect `git diff --find-renames --summary`, compile affected modules, and remove empty legacy package directories.
- This project is in the development and exploration stage. Do not preserve old databases, serialized payloads, configuration fields, APIs, or compatibility adapters unless the user explicitly requests it; choose the cleanest current design, allow destructive local-data recreation, and do not add historical migrations.
- Do not leave forwarding wrappers, duplicate implementations, or legacy adapters without an explicit current requirement.
- Avoid drive-by package moves or broad unrelated cleanup.
- Never commit build outputs, caches, generated reports, IDE state, API keys, tokens, or credentials. Store local secrets in ignored `local.properties` or environment variables and revoke exposed credentials.
- Treat untracked `app/release/` APKs as local artifacts; include or delete them only when explicitly requested.
- Before committing, inspect staged paths, run `git diff --cached --check`, exclude generated/local artifacts, and confirm architecture boundaries.
- Keep commits focused with imperative subjects under 72 characters. Pull requests describe user-visible changes, tests, relevant issue/OpenSpec change, and Compose screenshots.

## Working Files and Caches

- Place any temporary working files, scratch artifacts, downloaded assets, or intermediate caches in the repository-root `temp/` directory (e.g. `temp/<purpose>/...`), not in scattered locations inside modules or the repo root.
- `temp/` is git-ignored; treat its contents as ephemeral and safe to delete at any time. Do not commit anything under `temp/`.
- When a tool needs a default cache directory (Gradle, IDE, SDK downloads, etc.), redirect it to a subdirectory of `temp/` or keep it under an already-ignored top-level path such as `.gradle`, `.idea`, or `.kotlin`; do not introduce new top-level cache directories.

## Android Tooling and Physical Device Verification

- Prefer the `android` CLI for project, deployment, launch, screenshot, layout, and emulator workflows; consult `android help <command>` first.
- Use plain `android run` for deployment/launch, `android layout` for primary UI inspection, `android layout --diff` for focused changes, and `android screen capture` for secondary visual checks. Always inspect captured PNGs visually.
- Do not pass `--debug` during routine physical-device installation, launch, UI, layout, screenshot, or interaction verification. On some devices it enables “Waiting For Debugger”, preventing the Activity and Compose hierarchy from appearing. Use `android run --debug` only when the task explicitly requires attaching a debugger; otherwise launch normally before inspecting the UI.
- Do not use `adb` by habit. Fall back only when the installed CLI is unavailable/fails or lacks the operation, such as connection diagnostics, wake, raw input, or a narrow shell command. Record the reason, limit ADB to that operation, then return to the CLI.
- Prefer a connected physical Android device for installation, UI, permissions, system insets, and hardware behavior. Never hard-code a manufacturer, model, or serial; resolve the current physical device dynamically. Use an emulator only when no suitable device exists or the task requires one, and report the fallback.

If CLI device resolution fails, `adb devices -l` is permitted only for discovery. Build and run on the selected physical device with:

```powershell
.\gradlew.bat app:assembleDebug
android run --device "$serial" --apks=app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
```

Use `android layout --device "$serial" --pretty` for hierarchy/text/bounds and `android screen capture -o build\device-screen.png` for visuals. Use narrowly scoped `adb -s "$serial" shell input ...` only when CLI lacks required input.

If the device is asleep or not foregrounded and CLI has no wake command, use only `adb -s "$serial" shell input keyevent KEYCODE_WAKEUP`, then re-check with `android layout` or `android screen capture`. Never bypass device security. Report persistent test changes such as model selection.
