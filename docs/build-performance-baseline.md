# 构建性能基线

本基线测量当前重构后的工作区，用于后续同环境比较；没有重构前的对应数据，不能据此计算本轮优化的提速比例。

## 复现方法

在项目根目录准备 JDK、Android SDK 和 Gradle 缓存后运行：

```bash
python3 -B .github/scripts/profile_build.py --incremental
```

Windows 同样通过 Python 调用，脚本使用 `gradlew.bat`。默认离线、2 个 workers、每种场景 3 次；缺少依赖时可加 `--online`，但下载时间会影响结果。只测无改动构建可省略 `--incremental`。在本次云工作区先运行 `source temp/onboarding/activate.sh`；该临时环境脚本不是项目运行要求。

脚本先预热一次 `:app:assembleDebug`，然后测无改动构建。增量探针仅临时改变 `ChatViewModel` 的私有日志标签，每次使用唯一值，不修改公开签名、注解或存储格式。测量期间不要编辑该文件或同时运行 Gradle。脚本保存原始源码，并在 `finally` 中恢复源码、重新构建正式 APK；检测到并发源码变化时保留备份并报错，不覆盖他人的修改。强制杀进程无法保证执行 `finally`，可用报告目录中的 `probe-original.kt` 核对恢复。

结果写入忽略的 `temp/build-profile/<UTC时间>/`，包括每次日志、Gradle HTML profile、源码备份和 `summary.json`。摘要记录 Java/系统、CPU 数、Gradle 配置、HEAD/脏文件清单、墙钟时间、配置缓存复用和任务结果。原始日志及源码备份只用于本地复现，不自动上传。复测时固定机器、workers、任务、源码基线和缓存条件，比较相同场景的中位数及范围。

## 2026-10-04 测量

环境：Linux x86_64、3 个可见逻辑 CPU、Temurin JDK 21.0.8、Gradle 9.4.1、2 个 workers；Gradle parallel/build cache/configuration cache 开启，已有依赖与构建产物，离线运行。工作区包含本聊天中尚未提交的优化。

| 场景 | 三次墙钟耗时（秒） | 中位数 | 范围 |
| --- | --- | --- | --- |
| 无改动 `app:assembleDebug` | 2.820 / 2.615 / 2.863 | 2.820 | 2.615–2.863 |
| 聊天私有实现单文件变化后 `app:assembleDebug` | 9.709 / 12.122 / 8.691 | 9.709 | 8.691–12.122 |

预热耗时 11.740 秒，首次存储该任务组合的配置缓存，不计入场景中位数。正式六次均复用配置缓存。无改动场景没有重新执行构建任务；每次增量场景重新执行 9 个任务，集中于聊天模块的处理/编译/变换/产物和 app 的 dex 合并/打包。domain/data 模块与 app Kotlin 编译保持 UP-TO-DATE。

增量场景的主要任务耗时如下（Gradle profile）：

| 任务 | 三次耗时（秒） |
| --- | --- |
| `feature:chat:kspDebugKotlin` | 2.254 / 2.402 / 2.277 |
| `feature:chat:compileDebugKotlin` | 1.897 / 1.547 / 1.543 |
| `app:packageDebug` | 2.176 / 2.663 / 1.433 |
| `app:mergeLibDexDebug` | 0.742 / 1.617 / 0.648 |

任务可并行，任务耗时不能直接相加当成墙钟或关键路径。源码已恢复；恢复后的 APK 构建成功，耗时 4.540 秒，部分编译产物从 build cache 恢复。

原始基线：`temp/build-profile/20261004T033033.364279Z/summary.json`。目录为临时产物，长期保留的是本文结果和可复用脚本。

## 下一步依据与限制

此次实现变更没有触发上游 domain/data 重编译，模块边界能限制传播范围。优先检查聊天 KSP/Hilt 的增量输入与生成代码范围，以及 APK 打包成本；是否调整注入方式需要保持 Hilt 正确性，并用相同基线比较。

这是一个私有实现变更探针，不代表所有 Kotlin 编辑。公开 API、序列化类型、Hilt/Room 注解、资源或 Gradle 脚本变化需要另建场景。没有测量 clean build、冷 daemon、CI 全量测试、release/R8、APK 大小或设备启动/内存性能，也没有因单次 profile 结果合并模块或关闭必需的注解处理器。

## KSP/Hilt 与 JVM ABI 诊断（2026-10-04）

脚本新增 `--probe viewmodel|lifecycle` 与 `--diagnostics`。诊断模式追加 `--info -Pksp.incremental.log=true`，记录每次聊天 KSP 生成文件的内容哈希及新增/删除/修改清单。诊断选项会改变 KSP 任务输入，首次预热可能重跑多个模块；只比较相同选项的正式场景，不与上面的普通模式耗时直接比较。

```bash
python3 -B .github/scripts/profile_build.py --incremental --probe lifecycle --diagnostics
```

检查结果：聊天模块有两个 Hilt ViewModel 和一个存储绑定模块，共 22 个 KSP 生成文件。修改 ViewModel 私有日志标签时，Gradle 因 `kspConfig.javaSourceRoots` 中的源文件变化执行聊天 KSP；22 个文件内容哈希不变，app 的 KSP/Kotlin 编译保持 UP-TO-DATE。Hilt 2.60 的 aggregating task 默认已开启，当前增量任务也没有重跑 app 的 Hilt 聚合，因此没有额外切换构建开关或绕过必要处理器。

无注解的 `ChatRunLifecycleCoordinator` 探针则发现意外的 ABI 传播：私有 companion 中未显式声明 private 的 `const val TAG` 在 JVM 外层类上生成 `public static final String TAG`。改变常量值时，app 的 KSP、Kotlin 编译、Hilt 类收集和类变换额外执行。将日志常量显式改成 `private const val` 后，JVM 字段成为 private，同一变更不再影响这些下游任务。同样修正导航和失败恢复协作者的内部日志常量。

| 相同诊断选项的生命周期探针 | 正式样本 | 执行任务数 | app KSP/Kotlin 编译 |
| --- | --- | --- | --- |
| 修复前 | 11.230 秒（1 次） | 13 | 重新执行 |
| 修复后 | 10.077 / 10.700 / 8.912 秒（3 次） | 每次 9 | 每次 UP-TO-DATE |

修复后三次中位数 10.077 秒，所有聊天 KSP 生成文件哈希均未变化。修复前仅一个样本，不据此宣称稳定提速比例。确定的改善是减少实现细节引发的下游编译传播，而非关闭聊天 KSP。

诊断原始报告分别为 `temp/build-profile/20261004T033738.581015Z`（ViewModel）、`20261004T033854.563698Z`（生命周期修复前）、`20261004T034012.584135Z`（修复后）。源码和恢复后的 APK 均由脚本恢复，日志常量的显式私有化作为正式修改保留。
