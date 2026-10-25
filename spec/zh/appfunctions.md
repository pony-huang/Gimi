# AppFunction 执行会话

[English](../en/appfunctions.md) · 简体中文

跨应用 AppFunction 需要 `android.permission.EXECUTE_APP_FUNCTIONS`。许多设备将
这项权限定义为系统特权权限，因此 Android 设置里通常没有对应开关，
`adb shell pm grant` 也无法授权。

Gimi 的正式版和调试版都包含一个 `Instrumentation` 测试入口。通过 ADB 显式启动
入口后，Gimi 会临时借用持有 AppFunction 权限的 Shell 身份。借用的身份只在
Instrumentation 会话及 Gimi 进程存活期间有效。

这是开发测试方式。从桌面正常启动 Gimi、重启手机、强制停止 Gimi，或更新/重装
APK 都会结束会话，之后需要重新执行启动命令。

## 准备工作

- 支持 AppFunction 的 Android 16 或更高版本设备。
- 已开启开发者选项，以及 USB 调试或无线调试。
- 当前 APK 包含 `AppFunctionShellInstrumentation`；旧版 APK 没有该入口。
- 电脑已安装 `adb`。
- 在 Windows 上使用脚本时需要 PowerShell。下文的 ADB 手动命令可用于其他系统。

先确认电脑能识别目标设备：

```powershell
adb devices -l
```

连接了多台设备时，从输出中复制目标设备的序列号，并通过 `-Serial` 或 `-s` 指定。

## 正式版

在仓库根目录构建正式版：

```powershell
.\gradlew.bat :app:assembleRelease
```

arm64 安装包通常位于
`app\build\outputs\apk\release\app-arm64-v8a-release.apk`。查明设备序列号后，
使用 Android CLI 安装：

```powershell
android install --device "<序列号>" --apks=app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

也可以使用 ADB：

```powershell
adb -s "<序列号>" install -r "app\build\outputs\apk\release\app-arm64-v8a-release.apk"
```

覆盖安装时，正式版 APK 必须与手机中已安装应用使用相同的签名证书。未提供正式
签名配置时，本地 Release 构建可能回退到 Debug 证书。若签名不一致，不要仅为
安装而卸载原应用，否则会丢失本地数据；请改用与已安装应用证书一致的 APK。

启动临时执行会话：

```powershell
.\start-appfunctions.ps1 -Variant Release -Serial "<序列号>"
```

保持这个终端窗口运行。预期的首段输出包含：

```text
INSTRUMENTATION_STATUS: AppFunctionShell=permission granted
```

随后打开 Gimi，进入 **设置 → AppFunciton**，下拉刷新应用列表。原先的
“尚未获得执行权限”提示应被可发现的应用列表替代。

## 调试版

需要在独立的调试包中测试时，构建、安装并启动调试版：

```powershell
.\gradlew.bat :app:assembleDebug
android install --device "<序列号>" --apks=app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
.\start-appfunctions.ps1 -Variant Debug -Serial "<序列号>"
```

调试版包名是 `github.ponyhuang.gimi.debug`，正式版包名是
`github.ponyhuang.gimi`。

## 不通过脚本手动启动

正式版和调试版使用相同的 Instrumentation 类：

```powershell
# 正式版
adb -s "<序列号>" shell am instrument -w `
  github.ponyhuang.gimi/github.ponyhuang.gimi.appfunction.AppFunctionShellInstrumentation

# 调试版
adb -s "<序列号>" shell am instrument -w `
  github.ponyhuang.gimi.debug/github.ponyhuang.gimi.appfunction.AppFunctionShellInstrumentation
```

在单行命令中去掉 PowerShell 的反引号和换行。测试期间不要结束这条 `adb` 命令。

## 结束或重新开启会话

在运行脚本的终端按 `Ctrl+C`。如果终端已经关闭，可强制停止对应包来结束会话：

```powershell
# 正式版
adb -s "<序列号>" shell am force-stop github.ponyhuang.gimi

# 调试版
adb -s "<序列号>" shell am force-stop github.ponyhuang.gimi.debug
```

下一次测试时重新运行启动命令。

## 常见问题

| 现象 | 处理方式 |
| --- | --- |
| 提示 `Install the Gimi ... APK` | 安装指定变体的应用包。 |
| 提示 `lacks the AppFunction session entry` | 已安装 APK 早于此次接入，或选错了正式版/调试版。重新构建并安装当前 APK。 |
| 提示 `Specify -Serial` | 同时连接了多台设备。运行 `adb devices -l` 并传入目标序列号。 |
| 提示 `Device did not grant the Shell AppFunction identity` | 当前设备或系统策略未开放该 Shell 身份。改用支持该方式的设备或系统构建；普通权限授权无法绕过。 |
| 应用仍提示需要权限 | 确认启动命令仍在运行、Gimi 进程没有重启，并在会话启动后重新进入 **设置 → AppFunciton**。 |
| 应用列表为空 | 下拉刷新。新安装或更新的提供方应用可能需要等待系统完成索引。 |
| 更新或重启后会话失效 | 按上文重新运行脚本或手动 ADB 命令。 |

`adb shell cmd app_function ...` 可用于检查提供方应用声明的函数，但执行这个系统
命令不会让另外启动的 Gimi 进程获得权限。

背景资料：[Android AppFunction 概览](https://developer.android.com/ai/appfunctions)、
[官方 AppFunctions 示例](https://github.com/android/appfunctions)。
