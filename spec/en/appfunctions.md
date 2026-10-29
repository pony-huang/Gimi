# AppFunction execution sessions

English · [简体中文](../zh/appfunctions.md)

Cross-app AppFunctions require `android.permission.EXECUTE_APP_FUNCTIONS`.
Many devices define this permission as a privileged permission, so there is
usually no switch in Android Settings and `adb shell pm grant` does not work.

Gimi includes an `Instrumentation` entry point in both release and debug builds.
When you start that entry point explicitly from ADB, Gimi temporarily adopts the
Shell identity that holds the AppFunction permission. The borrowed identity lasts
only while the instrumentation session and Gimi process remain alive.

This is a developer testing path. Launching Gimi from the home screen, rebooting,
force-stopping Gimi, or updating/reinstalling the APK ends the session. Start it
again afterward.

## Prerequisites

- Android 16 or later on a device that supports AppFunctions.
- Developer options and USB or wireless debugging enabled.
- A current Gimi APK that includes
  `AppFunctionShellInstrumentation`; older APKs do not have this entry.
- `adb` available on the computer.
- Windows PowerShell for `start-appfunctions.ps1`. The manual ADB command below
  works from any platform.

Confirm that the computer can see exactly one intended device:

```powershell
adb devices -l
```

If several devices are connected, copy the target device's serial number and pass
it with `-Serial` or `-s`.

## Release APK

Build the release APK from the repository root:

```powershell
.\gradlew.bat :app:assembleRelease
```

The arm64 artifact is normally at
`app\build\outputs\apk\release\app-arm64-v8a-release.apk`. Install it with the
Android CLI after resolving the serial from `adb devices -l`:

```powershell
android install --device "<serial>" --apks=app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

Or install with ADB:

```powershell
adb -s "<serial>" install -r "app\build\outputs\apk\release\app-arm64-v8a-release.apk"
```

The release APK must be signed with the same certificate as the installed app for
an in-place update. A locally built release can fall back to the debug certificate
when release signing properties are absent. Do not uninstall the existing app to
work around a signature mismatch unless you are willing to lose its local data;
use an APK signed with the installed app's certificate instead.

Start a temporary execution session:

```powershell
.\start-appfunctions.ps1 -Variant Release -Serial "<serial>"
```

Keep this terminal open. The expected initial output includes:

```text
INSTRUMENTATION_STATUS: AppFunctionShell=permission granted
```

Then open Gimi and go to **Settings → AppFunciton**. Pull down to refresh the
application list. The permission-required message should be replaced by the
discoverable AppFunction applications.

## Debug APK

Build and install the debug APK when you want the isolated debug package:

```powershell
.\gradlew.bat :app:assembleDebug
android install --device "<serial>" --apks=app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
.\start-appfunctions.ps1 -Variant Debug -Serial "<serial>"
```

The debug package name is `github.ponyhuang.gimi.debug`; the release package name
is `github.ponyhuang.gimi`.

## Start without the PowerShell script

The release and debug packages use the same instrumentation class:

```powershell
# Release
adb -s "<serial>" shell am instrument -w `
  github.ponyhuang.gimi/github.ponyhuang.gimi.appfunction.AppFunctionShellInstrumentation

# Debug
adb -s "<serial>" shell am instrument -w `
  github.ponyhuang.gimi.debug/github.ponyhuang.gimi.appfunction.AppFunctionShellInstrumentation
```

For a single-line command, remove PowerShell's backtick and line break. Keep the
`adb` command running while you test.

## Stop or renew the session

Press `Ctrl+C` in the terminal running the script. To terminate the session even
if the terminal was closed, force-stop the selected package:

```powershell
# Release
adb -s "<serial>" shell am force-stop github.ponyhuang.gimi

# Debug
adb -s "<serial>" shell am force-stop github.ponyhuang.gimi.debug
```

Run the start command again for the next session.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| `Install the Gimi ... APK` | Install the package for the requested variant. |
| `lacks the AppFunction session entry` | The installed APK predates this integration or is not the variant you selected. Build and install a current APK. |
| `Specify -Serial` | Multiple devices are visible. Use `adb devices -l` and pass the intended serial. |
| `Device did not grant the Shell AppFunction identity` | The device or OS policy does not expose this Shell identity. Use a supported device/build; a normal permission grant cannot bypass this restriction. |
| App shows the permission-required message | Confirm the start command is still running, the Gimi process was not restarted, and you opened **Settings → AppFunciton** after the session started. |
| AppFunction list is empty | Pull down to refresh. Newly installed provider apps may need time to be indexed. |
| Session ends after an update or reboot | Start a new session with the script or manual ADB command. |

`adb shell cmd app_function ...` is useful for inspecting provider functions, but
running that system command does not give the independently launched Gimi process
the permission.

Background: [Android AppFunctions overview](https://developer.android.com/ai/appfunctions),
[official AppFunctions sample](https://github.com/android/appfunctions).
