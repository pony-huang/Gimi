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

## 诊断与结果报告

先检查现有逻辑和运行证据。原因仍不明确时，使用聚焦测试复现行为并验证假设，非核心代码也适用。
诊断测试不要求采用 TDD；是否保留取决于回归价值。
重跑前检查准确的失败测试和原因。修复源码或夹具后重跑相同的聚焦检查；无关失败单独报告，未变化行为复用已通过结果。
同一基础设施原因重复失败，且没有新证据支持再次尝试时，停止重试。记录阻塞与剩余人工验证；编译成功不能替代缺失的测试或设备验证。

分别报告编译、JVM 测试、APK 打包、部署和设备验证。区分实际构建时间、IDE/对话框等待和基础设施重试。
测试与打包组合命令的总耗时不等于单测执行时间。
