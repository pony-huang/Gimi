# A2UI 业务执行

校对日期：2026-11-07，基于 `feature/realtime-voice` 分支。读者：改动 A2UI 生成链路、协议校验、聊天 catalog 组件、或升级 androidx a2ui 依赖的 agent 与开发者。英文版：[English](../../en/a2ui/a2ui-execution.md)。

新增/修改 catalog 组件、改 `generate_a2ui` 工具契约、改 surface 生命周期/重试语义、或升级协议依赖之前，先读本文。本文吸收了根 `AGENTS.md` 原有的 "Compose A2UI (agentic UI) reference" 一节。

## 协议背景与权威参考

A2UI（agentic UI）让 LLM agent 通过产出 JSON 生成原生化 Compose 交互界面。agent 绝不生成或执行任意 UI 代码；**组件 catalog 就是契约**，声明 agent 可用的组件、属性与函数。运行时与 UI 层与设计系统无关——本项目用自建聊天 catalog 渲染。

权威参考（协议持续演进，采用或升级前重新抓取）：

- Compose 渲染器页面：<https://developer.android.com/develop/ui/compose/agentic.md.txt>（抓取于 2026-10-19）
- A2UI 协议主页：<https://a2ui.org/>；规范：<https://a2ui.org/specification/v0.9.1-a2ui/>；catalogs：<https://a2ui.org/concepts/catalogs/>

依赖（全部 `1.0.0-alpha01`，见 `gradle/libs.versions.toml`）：`androidx.a2ui:a2ui-engine`（解析/schema 校验，仅 `:data:a2ui`）、`androidx.a2ui:a2ui-model`（`:data:a2ui`、`:feature:chat`）、`androidx.a2ui.compose:compose-runtime` 与 `compose-ui`（Snapshot 细粒度更新、catalog、渐进渲染；仅 `:feature:chat`）、`androidx.compose.material3:material3-a2ui`（Basic Catalog + `A2uiSurface` 宿主；仅 `:feature:chat`）。

## 一分钟总览

```text
LLM 发出 generate_a2ui 工具调用（intent / target_surface_id / changes / component_names）
   |  A2uiToolContribution — 仅当 a2uiSupported 且设置开启时声明
   v
A2uiGeneration — 重放已有 surface 状态、把 catalog 收缩到用到的组件、
   |             隔离的纯 UI 子代理渲染 JSON（无业务工具）、1 次自我纠错重试
   v
A2uiCreateScope — 立即返回 pending；生成在旁路 scope 进行
   v
AndroidxA2uiProtocol.validate (data/a2ui) — 官方引擎解析 + schema + 图检查
   v  （以 author="a2ui_generation" 的 Event 持久化；PENDING/COMPLETED/FAILED/CANCELLED）
EventMapper / ChatRunEventMapper — 以 generate_a2ui function-response 视图呈现
   v
ChatA2uiCoordinator (feature/chat) — 从消息推导 A2uiRecord、重放、喂差异
   v
A2uiSurface (material3-a2ui) 在所属 MessageBubble 内渲染卡片
   ^
   +--- 用户交互 -> A2uiInteraction -> 作为新聊天回合发送（a2ui_interaction 信封）
   +--- FAILED/CANCELLED 卡片 -> Retry 按钮 -> ChatAgentRepository.retryA2ui
```

## 模块所有权

| 模块 | 拥有 | 不得拥有 |
|---|---|---|
| `:domain:a2ui` | `A2uiGenerationTask`（含状态/编解码）、`A2uiCapabilities`、`A2uiDocument`、`A2uiInteraction`、`A2uiRecord`、`A2uiProtocol` 接口、交互/工具结果编解码——纯 JVM（仅 kotlinx-serialization） | Compose、Android、Hilt、引擎类型 |
| `:data:a2ui` | `AndroidxA2uiProtocol`（经官方引擎 validate + replay）、`CatalogSchema`、`CatalogComponentReferences`、`ProtocolJsonReader` | Compose；执行宿主函数 |
| `:data:agent/a2ui` | `A2uiToolContribution`、`A2uiGeneration`（含 catalog 收缩、路由指令）、`A2uiCreateScope`、`A2uiCreateRetry` | 业务工具执行 |
| `:feature:chat/a2ui` | `ChatA2uiCatalog` 自定义组件、`ChatA2uiCoordinator`（会话同步/提交）、主题、surface 宿主、preview | agent 装配；协议解析 |

会话管道：`ChatRunModels` 携带 a2ui 视图；`A2uiTaskMessages` 按 `surfaceId` 替换任务快照、无活跃运行时把遗留 PENDING 翻成 CANCELLED；`data/conversation EventMapper` 把 `a2ui_generation` 作者的事件转成合成的 `generate_a2ui` function-response，让调用/响应芯片配对。

## 生成链路（data/agent/a2ui）

- **工具契约**：`generate_a2ui(intent, target_surface_id, changes, component_names)`。`processLlmRequest` 注入动态路由指令（由能力 schema 生成）：A2UI 强制场景 vs Markdown、逐组件路由（容器、表格、天气、音乐、选项卡）、以及如何处理 `a2ui_interaction` 提交。
- **catalog 收缩**：生成 schema 只含基础组件 + 请求组件 + 其 `$ref` 闭包；未被引用的 `$defs` 被剥离（否则 schema 的任意组件构造会把整个 catalog 拖进每次校验）。更新时经 `previousComponentNames` 保活先前组件。
- **子代理**：隔离的纯 UI agent（自备模型、无业务工具），强制单次 `render_a2ui` 调用（`Mode.ANY`）。校验失败时把错误文本追加为纠错提示重试一次；二次失败返回工具失败。网络/SDK 错误绝不重试。
- **异步创建**：`A2uiCreateScope` 立即返回 `pending`，生成在旁路 scope 进行；runner 流结束后结果归档为事件——取消经 `NonCancellable` 持久化为 CANCELLED，失败持久化为 FAILED（保留重试入口）。
- **重试**：仅限 FAILED/CANCELLED 任务；从归档的 `changes` 重跑生成，无父运行、无业务工具。

## 校验链路（data/a2ui）

`AndroidxA2uiProtocol.validate` 依次：surfaceId 非空 → 官方消息解析器 → 各消息 surfaceId 一致 → 删除修订只含一条删除消息 → 否则恰好 `createSurface`/`updateDataModel`（根 `/`）/`updateComponents` → catalogId 匹配（无协商——单一 catalog）→ 1..256 个唯一组件 id 且有 `root` → 图完整性（子引用存在、无环）→ 经官方 `A2uiCoreMessageProcessor` 做 5 秒超时保护的全量 schema 校验。**超时被归一化为 `IllegalArgumentException`**，让它进入子代理的纠错循环而不是静默取消调用方 scope。`replay(records)` 把修订/提交折叠成最终文档（提交覆写 data-model 根，恢复的历史保留最后提交的表单值）。

## 渲染链路（feature/chat/a2ui）

- **Catalog**：`chatA2uiCatalog` —— catalogId `https://gimi.app/a2ui/catalogs/chat/v1`，inline，Material Basic Catalog 原语 + 自定义组件：`ChatA2uiText/Button`、容器（`Disclosure`/`Tabs`/`DetailSheet`）、`WeatherToday`、`MusicPlayer`、`DataTable`、`OptionDeck`、媒体渲染器（coil3 图片、视频/音频播放）、画廊预览。自定义组件覆写 `isReady` 实现逐组件加载态；`ChatA2uiChild` 渲染 Success/Loading/Error。
- **协调器**：每会话一个官方 `A2uiMessageProcessor`；提交经 `canSubmit` 门控（agent 未在运行）。`sync` 对重放记录做差异：仅数据变化只补丁 `updateDataModel`（删除仍在显示的 surface 会因必需绑定缺失导致 Compose 崩溃）；否则删除 + 重推。出站客户端事件变成 `A2uiInteraction` 聊天回合（草稿不进历史）。
- **宿主 UI**：`ChatA2uiContent` 无状态，按 surface id 渲染，另有 `A2uiTaskStatus` 占位卡（转圈/失败/取消 + 重试）。surface 在 `ChatScreen`/`MessageBubble` 按所属消息过滤。`ChatA2uiTheme` 只覆写字号/形状——应用配色与系统字体缩放直通。

## 新增自定义 catalog 组件

1. 在 `:feature:chat/a2ui` 实现组件（组件 + 数据契约 + 明暗双套 `*Previews.kt`），注册进 `ChatA2uiCatalog`——其定义随后进入生成与校验共享的能力 schema 导出。
2. 若 LLM 需要 schema 表达不了的路由规则，在 `A2uiGenerationInstructions`/`A2uiGeneration` 加条件指令块（仅当收缩后的 catalog 含该组件时注入）。
3. 生成收缩会自动带上新组件：模型经 `component_names` 请求时进入 schema，再经 `$ref` 闭包到达校验器——除非用到新的 common-type 形状（那要核对 `CatalogSchema` 的 `common_types` 映射），无需在 `:data:a2ui` 单独注册。
4. 跑 `ChatA2uiCatalogValidationTest`、`A2uiComponentGraphTest` 与 data/a2ui 协议测试；确认 preview 明暗都渲染正常。

## 横切规则

1. **不可信输入**：agent JSON 先经官方引擎解析与 schema 校验才进 UI 状态；错误态可见（逐组件 Loading/Error）；业务动作仍是常规授权工具——提交只携带结构化交互数据。
2. **分层边界**：只有 `:feature:chat` 碰 Compose a2ui 依赖；`:data:a2ui` 用引擎但不碰 Compose。唯一有意保留的反向边是 `:feature:chat` `testImplementation(project(":data:a2ui"))`，让 JVM 测试校验真实 catalog↔校验器契约。
3. **i18n**：宿主字符串在 `feature/chat` 的 `chat_a2ui_*`（中文默认 + `values-en`）；提示词要求可见标签用用户语言；catalog 函数用 ICU `MessageFormat`。
4. **Preview 优先**：每个自定义组件都带 `*Previews.kt` 明暗双套 fixture；不加自动化 UI 交互测试。
5. **R8**：目前无 a2ui 专属 proguard 规则——androidx 依赖自带 consumer 规则。

## 升级清单

1. 重新抓取 Compose 渲染器页面与 a2ui.org 规范；确认当前协议版本，不要默认仍是 0.9.1。
2. 五个依赖保持版本对齐；检查接口弃用（如 `A2uiComponent` → `A2uiComponentV2` 式）与 catalog 协商行为变化。
3. 更新硬编码的 `"v0.9.1"` 字符串（`A2uiGeneration.kt` 消息构造、`ChatA2uiCoordinator.kt` 删除消息）；catalog schema 变化时同步更新 catalogId。
4. 核对 `CatalogSchema` 的 `common_types` 映射仍覆盖官方定义；重跑 `ChatA2uiCatalogValidationTest`、`A2uiComponentGraphTest` 与 data/a2ui 协议测试。

## 坑

- 能力开关进 `AgentKey`（`a2uiSupported`），因此无能力执行（如实时语音）永不声明 `generate_a2ui`；设置开关进 a2ui contribution 的 `revision()` 以重建缓存运行时。
- OptionDeck 事件的 `round` 从 0 起；`round > 0` 表示本地重启的卡组在重复 itemIds——重跑有副作用的工具前视为需重新过目。
- `ChatA2uiSubmissionTest` 用 mockk 把 `ProtocolJsonReader` 替换进官方解析器（JVM 没有 Android JsonReader）——脆弱但有意为之，不要「顺手简化」掉。
- 完成事件作者是 `a2ui_generation` 而非模型；历史映射对该作者有特判。
