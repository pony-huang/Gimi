# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application. The `app/` module contains all production code and resources:

- `app/src/main/java/github/ponyhuang/asssistantai/`: Kotlin source, organized by responsibility: `ui/` (Compose screens and components), `data/` (Room persistence and providers), `agent/` (model clients), `di/` (Hilt bindings), `model/`, and `appfunctions/`.
- `app/src/main/res/`: Android resources, including strings, themes, drawables, and launcher icons.
- `app/src/test/`: JVM unit tests; `app/src/androidTest/`: device/emulator instrumentation and Compose UI tests.
- `gradle/libs.versions.toml`: centralized dependency and plugin versions. Notes live in `doc/`.

Keep new code in the package matching its feature or layer; avoid putting unrelated features in `MainActivity.kt`.

## Build, Test, and Development Commands

Run commands from the repository root (use `gradlew.bat` on Windows):

- `./gradlew assembleDebug` builds the debug APK.
- `./gradlew test` runs all JVM unit tests.
- `./gradlew connectedAndroidTest` runs instrumentation tests on a connected emulator or device.
- `./gradlew check` runs the configured verification lifecycle tasks.
- `./gradlew clean` removes generated build output.

Open the project in Android Studio to run the `app` debug configuration on an emulator or device.

## Coding Style & Naming Conventions

Write idiomatic Kotlin with four-space indentation and trailing commas where surrounding code uses them. Use PascalCase for classes, composables, and primary-type files (`ChatViewModel.kt`); use camelCase for functions, properties, and local values. Keep Compose state in `*UiState` types and screen logic in `*ViewModel` classes. Prefer constructor injection and Hilt modules. Use Material 3 theme tokens in `ui/theme/` instead of hard-coded UI colors.

No formatter or linter is currently configured; match nearby code and keep imports organized by the IDE.

## Compose UI Safety Insets

When designing or changing Compose UI, always account for system safety insets. Edge-to-edge screens must keep interactive controls and primary content clear of status bars, display cutouts, navigation bars, and gesture areas by applying the appropriate inset padding (for example, `statusBarsPadding`, `navigationBarsPadding`, or `safeDrawingPadding`). Visually review screenshot/device output to ensure back buttons, app bars, and bottom controls are not crowded by system UI.

## Testing Guidelines

Use JUnit 4 for unit tests in the matching `app/src/test` package. Name files `*Test.kt` and methods after the behavior checked, for example `fun restoresLastSession()`. Put Android-dependent and Compose interaction coverage in `app/src/androidTest`. Unless explicitly requested, feature work does not require adding or running unit tests; verify it with `./gradlew compileKotlin` instead. Do not make tests depend on live model-provider APIs.

If compilation succeeds but the same automated test repeatedly fails because of the test runner, device environment, or infrastructure rather than a reproducible code failure, stop retrying and hand the case off for manual verification. Record the successful compile command, the repeated test failure, and any manual verification already completed.

## Meizu Device UI Verification

The shared Meizu 20 Pro can be reached through wireless ADB when it appears in `adb devices -l`. Use the displayed serial dynamically rather than hard-coding it, then build and install the debug APK:

```powershell
$serial = (adb devices | Select-String '\sdevice$' | Select-Object -First 1).ToString().Split()[0]
.\gradlew.bat app:assembleDebug
adb -s $serial install -r app\build\outputs\apk\debug\app-debug.apk
adb -s $serial shell monkey -p github.ponyhuang.asssistantai -c android.intent.category.LAUNCHER 1
```

For visual Compose checks, use `adb shell input tap <x> <y>` to navigate, capture the screen with `adb -s $serial exec-out screencap -p > build\device-screen.png`, and inspect the PNG visually. Pair this with `adb -s $serial shell uiautomator dump /sdcard/window.xml` when confirming text, click targets, and bounds. Check that interactive elements clear the status bar and gesture-navigation area.

The device may be asleep. If an initial screenshot or UI dump shows the keyguard or the app is not foregrounded, first wake it with `adb -s $serial shell input keyevent KEYCODE_WAKEUP`, then re-check the UI before reporting a test blocker. The shared test device normally has no authentication lock, so waking it is sufficient; do not attempt to bypass a password, pattern, or biometric lock if one is actually present. Keep any intentional model-selection test change visible in the final report, because it persists in the app's settings.

## Commit & Pull Request Guidelines

Existing history uses short imperative subjects (for example, `Refactor settings module`); keep subjects under 72 characters and commits focused. Pull requests should explain user-visible changes, note tests run, link the relevant issue or OpenSpec change, and include screenshots for Compose UI changes.

## Security & Configuration

Never commit API keys, provider tokens, or credentials. Store local secrets in ignored `local.properties` or environment variables, and revoke credentials that enter source control.
