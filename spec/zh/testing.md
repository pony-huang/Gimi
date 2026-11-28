# 测试与验证

[English](../en/testing.md) · 简体中文

## 范围与验证

AGENTS.md 维护默认验证决策表和必需检查。本文说明测试质量、夹具和故障诊断，不为日常开发增加全量测试门槛。
针对变化的行为运行聚焦测试；跨模块契约、持久化、安全、生成代码、发布接线或结果存在不确定性时，再扩大验证范围。

## 测试价值与保留

保留保护业务行为、外部契约、风险边界或已修复缺陷的最小回归覆盖。保护目标不明显时，说明所保护的行为。
核心业务重构需要特征测试，核心规则变更需要归属层的行为测试。
临时假设验证和仅供开发使用的测试应在提交前删除，或移到被忽略的 `temp/`。临时测试通过本身不是保留理由。

避免仅重复实现、回显 mock 值，或跨层重复静态声明却无法发现独立失败的测试。
字面量预期、注册表条目和一行判断仍可能保护协议或权限边界，应按契约与发现错误的价值判断，不能按语法直接排除。
保留已有的有效测试；行为合理变化时，最小更新夹具或预期。仅在依赖确实不再使用时移除。

## 跨平台与隔离夹具

测试必须能从干净检出在本地 Windows 和 GitHub Actions Ubuntu 运行，不依赖执行顺序、缓存、凭据或机器配置。
使用准确的文件名大小写、跨平台路径、必要时显式指定 UTF-8 和 Locale，并明确处理换行。
考虑文件替换、文件锁和权限差异。使用隔离的临时文件与自包含夹具；排查平台差异时对齐 CI 工具链。
测试使用 fake、MockWebServer 或 mock gateway，禁止使用真实模型 API 或真实凭据。

## HTTP 与付费集成边界

独立 HTTP 或付费工具集成需要聚焦覆盖请求/响应映射、凭据来源、参数校验、失败/重试和取消行为。
适用时，为启用和选择规则增加领域测试。重复请求影响成本或正确性时，检查 HTTP 客户端的隐式重试。
每项行为明确主要测试归属层；仅为尚未覆盖的接线风险增加跨层测试。

## 设备级检查与基线配置

`app` 的导航冒烟测试（`AppNavigationSmokeTest`）在生产分发体 `AppEntryContent` 中逐个驱动全部类型安全 NavKey，宿主为 `DebugTestHostActivity`（debug 源集）。声明在 androidTest 清单里的宿主 Activity 会落在测试 APK 自己的进程，被 `ActivityScenario` 拒绝，因此宿主放在 debug 源集。运行：`ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest`；设备策略禁止安装新 instrumentation 包时（部分 Meizu 报 `INSTALL_FAILED_USER_RESTRICTED`），回退模拟器并记录。未注册的目的地会快速失败；带参目的地使用占位 id，应渲染空态/错误态而非崩溃。

基线配置在连接设备上生成并提交于 `app/src/release/generated/baselineProfiles/baseline-prof.txt`。修改 `MainApplication`/`MainActivity` 启动路径或首屏组合后重新生成：`ANDROID_SERIAL=<serial> ./gradlew :app:generateReleaseBaselineProfile`。`:baselineprofile` 的 `StartupBenchmark` 按设计拒绝模拟器，且需要允许安装 instrumentation 包的设备；基准变体带 `profileable` 并使用 debug 签名。

## 高频失败模式

CI 与本地反复出现几类失败，动手前先从日志分类。瞬态基础设施失败（依赖下载被限流返回 HTTP 429、网络抖动）在任何测试执行前就使构建失败；artifact 解析错误与状态码失败意味着重跑，而非改代码。真实测试失败必须查根因。

时序敏感测试让真实异步 I/O 与测试调度器赛跑，本地稳定也会在 CI 负载下闪失——本地 MockWebServer 的握手拒绝可先于测试协程的下一次调度到达。预防这一类：生产代码保持单一失败报告源（一个通道或异常路径拥有失败分类，不允许第二条路径抢先报错）；测试顺序确定性（先消费失败帧再触发发送）；断言失败分类而非时序窗口；修复后本地多次重跑验证——CI 绿一次不等于稳定。`runTest` 内跑真时钟（`Dispatchers.IO`）的看门狗是常见变体，测试不得依赖短调度窗口。

JVM 测试因未 mock 的 Android API（如 `android.util.Log`）失败：在途代码触碰这类 API 时，先确认归属模块的 Robolectric 或默认值配置，提交前运行该模块。

红测试不得无主滞留：当场修复，或记录归属与引入它的改动。CI 全绿是合并与发版的门禁，无主红测试会毒化后续所有信号。

保持测试工作区干净：写文件的测试使用临时目录，固定路径产物在 teardown 还原；提交前检查 `git status`（ObjectBox JSON 落盘曾踩坑）。

框架与平台坑会复发：MockK `every { }` 里的属性读取被同名形参遮蔽、domain 模块任务名是 `test`（非 `testDebugUnitTest`）、Robolectric 库模块须开 `isIncludeAndroidResources`。按模块类型选对任务名，选最小验证目标。

## 诊断与结果报告

先检查现有逻辑和运行证据。原因仍不明确时，使用聚焦测试复现行为并验证假设，非核心代码也适用。
诊断测试不要求采用 TDD；是否保留取决于回归价值。
重跑前检查准确的失败测试和原因。修复源码或夹具后重跑相同的聚焦检查；无关失败单独报告，未变化行为复用已通过结果。
同一基础设施原因重复失败，且没有新证据支持再次尝试时，停止重试。记录阻塞与剩余人工验证；编译成功不能替代缺失的测试或设备验证。

分别报告编译、JVM 测试、APK 打包、部署和设备验证。区分实际构建时间、IDE/对话框等待和基础设施重试。
测试与打包组合命令的总耗时不等于单测执行时间。
