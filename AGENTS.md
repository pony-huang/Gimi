# Repository Guidelines

## Project Structure & Module Organization

This is a multi-module Android application organized by feature and clean architecture boundaries. The current module graph declared in `settings.gradle.kts` is the source of truth:

- `:app` is the composition root. It owns application startup, top-level navigation, Android services/app functions, and Hilt wiring that composes multiple capabilities. It must not become a catch-all UI, domain, or data module.
- `:core:common`, `:core:network`, `:core:database`, and `:core:testing` provide narrow shared infrastructure.
- `:core:designsystem` owns theme tokens and stateless, business-agnostic Compose components.
- `:domain:<capability>` owns domain models, repository interfaces, and use cases for one capability. Current capabilities are `modelcatalog`, `conversation`, `speech`, `mcp`, `workfiles`, and `permissions`.
- `:data:<capability>` owns the corresponding repository implementations, Room/Preferences/Keystore storage, Android gateways, network clients, and third-party SDK adapters.
- `:feature:<capability>` owns feature UI, routes, contracts, ViewModels, and feature-specific components. Current features are `modelsettings`, `settings`, `mcp`, `workfiles`, `permissions`, `voicewake`, and `chat`.
- `gradle/libs.versions.toml` is the dependency/version catalog. Notes live in `doc/`.

Module ownership is more important than a legacy package name. New feature code should use a package matching its owning feature; do not recreate the old `app/ui`, `app/data`, `app/model`, or similar catch-all package trees.

## Architecture Preservation Rules

The required runtime direction is:

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
```

Keep module dependencies consistent with these rules:

```text
:app -> :feature:* + :data:* + required domain/core modules
:feature:* -> :domain:* + :core:designsystem
:data:* -> :domain:* + required core infrastructure
:domain:* -> Kotlin/coroutines and explicitly shared domain contracts only
feature A -X-> feature B
domain/data/core -X-> feature or app
```

These rules are mandatory for new work:

- A feature module must never import or depend on another feature module. Move shared business contracts to the appropriate domain module; move genuinely business-agnostic UI to `:core:designsystem`.
- Domain code must not depend on Android, Compose, Hilt, Room, OkHttp, provider SDKs, or data-layer models. Domain repository types are interfaces.
- Third-party SDK, HTTP, database, Keystore, Preferences, and Android framework details belong behind gateways or repository implementations in data/core infrastructure.
- ViewModels may depend on use cases and domain repository interfaces, never concrete repositories, DAOs, Context, Toast, navigation controllers, OkHttp, Room, or provider SDK objects.
- New branching business rules, cross-repository operations, or reusable workflows require a use case. Do not create pass-through use cases for trivial access unless they establish a needed contract boundary.
- `:app` may compose implementations but must not host feature screens, reusable business logic, or provider-specific client behavior.
- Prefer constructor injection and Hilt. Bind domain interfaces to data implementations in the owning data module; keep cross-capability runtime composition in `:app`.
- Do not introduce generic `Manager`, `Helper`, `Utils`, or `BaseViewModel` dumping grounds. Name types after one concrete responsibility and place them in the narrowest owning module.
- Do not move code into `core` merely because two files look similar. Promote code only when it is business-agnostic, has a stable contract, and has at least two real consumers.

When a requested implementation appears to violate the graph, change the contract or introduce an interface/use case instead of adding a shortcut dependency. Any temporary exception must be documented in the change description with an owner and removal condition; undocumented architecture exceptions are not allowed.

## Compose State and Side-Effect Rules

- Each screen exposes one immutable `*UiState` and receives user intent through a `*Action`/`*UiAction` contract or explicit synchronous callbacks.
- Route composables collect lifecycle-aware state and own Android/UI side effects such as navigation, Toast/Snackbar delivery, activity results, permissions, and opening URLs.
- Screen and business components must be stateless: state enters through parameters and events leave through callbacks. They must not fetch a ViewModel, repository, or Context internally or launch business coroutines.
- Keep ephemeral business operation state such as loading, testing, refreshing, and notices in the ViewModel state, not in reusable UI components.
- `:core:designsystem` components must not reference ViewModels, repositories, domain models, navigation, Toast, network calls, or business coroutines. They must be independently previewable and Compose-testable.
- Preserve existing theme tokens, dimensions, copy, accessibility semantics, and safety insets unless the task explicitly changes the UI.

## Change Placement Checklist

Before adding a file or dependency, decide its owner in this order:

1. Feature-only presentation or interaction state -> `:feature:<capability>`.
2. Business model, rule, repository contract, or reusable workflow -> `:domain:<capability>`.
3. Persistence, network, Android API, secure storage, or SDK adapter -> `:data:<capability>`.
4. Business-agnostic shared infrastructure/UI with multiple consumers -> the narrowest `:core:*` module.
5. Application startup, navigation graph, service registration, or cross-capability composition -> `:app`.

If no current capability owns the change, create a focused domain/data/feature slice rather than placing it in a nearby unrelated module. New Gradle modules require a clear owner and dependency direction; do not create empty placeholder modules.

## Build, Test, and Development Commands

Run commands from the repository root (use `gradlew.bat` on Windows):

- `.\gradlew.bat app:compileDebugKotlin` compiles the complete debug dependency graph.
- `.\gradlew.bat :feature:<name>:testDebugUnitTest` runs an affected feature's JVM tests; use the equivalent domain/data task for those modules.
- `.\gradlew.bat app:testDebugUnitTest` runs app JVM tests.
- `.\gradlew.bat app:assembleDebug` builds the debug APKs.
- `.\gradlew.bat connectedDebugAndroidTest` runs configured instrumentation tests on a connected emulator or device.
- `.\gradlew.bat check` runs the configured verification lifecycle tasks.
- `.\gradlew.bat clean` removes generated build output.

Open the project in Android Studio to run the `app` debug configuration on an emulator or device.

## Coding Style & Naming Conventions

Write idiomatic Kotlin with four-space indentation and trailing commas where surrounding code uses them. Use PascalCase for classes, composables, and primary-type files (`ChatViewModel.kt`); use camelCase for functions, properties, and local values. Keep Compose state in `*UiState` types and screen logic in `*ViewModel` classes. Prefer constructor injection and Hilt modules. Use Material 3 tokens from `:core:designsystem` instead of hard-coded UI colors.

No formatter or linter is currently configured; match nearby code and keep imports organized by the IDE.

## Compose UI Safety Insets

When designing or changing Compose UI, always account for system safety insets. Edge-to-edge screens must keep interactive controls and primary content clear of status bars, display cutouts, navigation bars, and gesture areas by applying the appropriate inset padding (for example, `statusBarsPadding`, `navigationBarsPadding`, or `safeDrawingPadding`). Visually review screenshot/device output to ensure back buttons, app bars, and bottom controls are not crowded by system UI.

## Testing Guidelines

Use JUnit 4 for JVM unit tests in the owning module's `src/test` source set. Name files `*Test.kt` and methods after the behavior checked, for example `fun restoresLastSession()`. Put Android-dependent and Compose interaction coverage in the owning module's `src/androidTest` source set.

- Before behavior-preserving refactoring, add characterization tests for current ViewModel state transitions and externally visible repository behavior.
- New or changed business rules require domain/use-case or ViewModel unit tests. New gateways and repository implementations require success, failure, exception, and mapping coverage as applicable.
- Stateless Compose components with meaningful interaction or conditional rendering require Compose UI coverage when practical.
- Pure visual-only changes may use compilation plus screenshot/device verification, provided interaction behavior is unchanged.
- Tests must use fakes, MockWebServer, or mocked gateways; never depend on live model-provider APIs or real credentials.
- At minimum, compile the affected module and `app:compileDebugKotlin`, run affected JVM tests, then run `git diff --check`. Use `app:assembleDebug` for changes affecting wiring, resources, manifests, or packaging.
- A feature is not complete if it compiles only because `:app` supplies an undeclared transitive dependency. Each module must declare and verify its own dependencies.

If compilation succeeds but the same automated test repeatedly fails because of the test runner, device environment, or infrastructure rather than a reproducible code failure, stop retrying and hand the case off for manual verification. Record the successful compile command, the repeated test failure, and any manual verification already completed.

## Refactoring, File Moves, and Repository Hygiene

- Refactor one closed capability at a time. Preserve observable behavior and keep each logical commit independently compilable and revertible.
- Use IDE Refactor Move or `git mv` for source moves. A move-only commit should change paths, packages, imports, and required visibility only; do not mix unrelated business changes into it.
- After moves, inspect `git diff --find-renames --summary`, compile affected modules, and remove empty legacy package directories.
- Never commit Gradle build outputs, local caches, generated test reports, IDE state, API keys, or provider credentials. Module-level `build/` directories are ignored by the root `.gitignore`.
- Treat untracked `app/release/` APKs as local artifacts. Include or delete release APKs only when the user explicitly requests that exact action.
- Do not leave forwarding wrappers, duplicate implementations, or legacy adapters unless compatibility is an explicit requirement. This prototype currently permits destructive local-data recreation; do not add historical data migrations without a requirement.
- Do not perform drive-by package moves or broad cleanup in an unrelated feature change.

## Meizu Device UI Verification

The shared Meizu 20 Pro can be reached through wireless ADB when it appears in `adb devices -l`. Use the displayed serial dynamically rather than hard-coding it, then build and install the debug APK:

```powershell
$deviceLine = adb devices -l | Select-String ' device ' | Select-Object -First 1
$serial = $deviceLine.ToString() -replace '\s+device\s+product:.*$',''
.\gradlew.bat app:assembleDebug
adb -s "$serial" install -r app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
adb -s "$serial" shell monkey -p github.ponyhuang.asssistantai -c android.intent.category.LAUNCHER 1
```

For visual Compose checks, use `adb -s "$serial" shell input tap <x> <y>` to navigate, capture the screen with `adb -s "$serial" exec-out screencap -p > build\device-screen.png`, and inspect the PNG visually. Pair this with `adb -s "$serial" shell uiautomator dump /sdcard/window.xml` when confirming text, click targets, and bounds. Check that interactive elements clear the status bar and gesture-navigation area.

The device may be asleep. If an initial screenshot or UI dump shows the keyguard or the app is not foregrounded, first wake it with `adb -s "$serial" shell input keyevent KEYCODE_WAKEUP`, then re-check the UI before reporting a test blocker. The shared test device normally has no authentication lock, so waking it is sufficient; do not attempt to bypass a password, pattern, or biometric lock if one is actually present. Keep any intentional model-selection test change visible in the final report, because it persists in the app's settings.

## Commit & Pull Request Guidelines

Existing history uses short imperative subjects (for example, `Refactor settings module`); keep subjects under 72 characters and commits focused. Pull requests should explain user-visible changes, note tests run, link the relevant issue or OpenSpec change, and include screenshots for Compose UI changes.

Before committing, confirm that the staged diff contains no generated files or local artifacts, `git diff --cached --check` passes, and architecture boundaries above are still satisfied.

## Security & Configuration

Never commit API keys, provider tokens, or credentials. Store local secrets in ignored `local.properties` or environment variables, and revoke credentials that enter source control.
