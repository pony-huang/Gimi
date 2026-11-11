# Agent 业务执行

校对日期：2026-11-07，基于 `feature/realtime-voice` 分支。读者：修改聊天/agent 执行、工具接线、运行生命周期或确认行为的 agent 与开发者。英文版：[English](../../en/agent/agent-execution.md)。

新增 `AgentContribution`、改动运行生命周期/确认语义、或触碰事件管线之前，先读本文。语音子系统见[实时语音系统](../voice/realtime-voice-system.md)；A2UI 生成/渲染管线见 [A2UI 业务执行](../a2ui/a2ui-execution.md)；AppFunctions 工具族见 [AppFunctions](../appfunctions.md)。

## 一分钟总览

Gimi 的 agent 不是持久化对象，而是按配置在运行时用 ADK-Kotlin 原语装配并缓存。一次聊天回合：

```text
ChatViewModel (feature/chat)
   |  ChatRunLifecycleCoordinator — 经 AgentRuntimeGate 获取运行租约/令牌（会话级忙碌状态）
   v
PrepareChatSendUseCase (domain/conversation) — 工具配置解析、回合归档
   v
ChatAgentRepository.send（领域接口）
   |  AdkChatAgentRepository (data/agent/conversation)
   v
AgentChatRunner.createExecution -> AgentChatExecution
   |  ADK Runner.runAsync，StreamingMode.SSE，RunConfig.customMetadata
   v
Flow<ADK Event>
   |
   +- 实时视图： Event.toDomain() -> ChatRunEvent -> AgentEventReducer（按 Event.id 部分合并）
   +- 历史：    data/conversation EventMapper 从 Session.events 重建 Message
   +- 持久化：  ADK SessionService（Room）是唯一存储；标题/记忆走 ADK 插件
```

**没有 `:domain:agent` 模块**——agent 业务契约（运行事件、仓库接口、运行时门）在 `:domain:conversation`；ADK 运行时只在 `:data:agent`。

## 模块所有权

| 模块 | 拥有 | 不得拥有 |
|---|---|---|
| `:domain:conversation` | 与 SDK 无关的运行模型（`ChatRunEvent`、`Message`、`ChatTurn`）、`ChatAgentRepository`/`ConversationRepository` 接口、`PrepareChatSendUseCase`、`ChatRunEventMapper`、`AgentRuntimeGate`/`AgentSessionIdentity` | 任何 Android、Compose、Hilt、Room、OkHttp、ADK 类型 |
| `:data:agent` | agent 装配（`AgentFactory`、`AgentChatRunner`）、全部 `AgentContribution` 实现、模型适配器、A2A + A2UI 运行时、聊天模式语音执行 | 持久化契约、UI 状态 |
| `:data:conversation` | `AdkConversationRepository`（Room 读侧）、`EventMapper`（历史重放）、附件归档、工具批准存储 | ADK agent 装配 |
| `:feature:chat` | `ChatViewModel` + 各协调器（运行生命周期、事件归并、恢复/工具配置/会话导航） | 仓库实现、ADK 类型 |
| `:domain:assistant` | 系统助手入口（蓝牙/分离式语音）——同一运行时的第二执行源 | 自己的 agent 运行时 |

其余领域模块（`mcp`、`skills`、`plugin`、`modelcatalog`、`memory`、`sandbox`、`a2ui`、`toolauthorization` 等）向 contribution 提供契约；`:plugin-api` + `:plugins:*` 是由插件 contribution 加载的动态 APK 插件。

## Agent 装配

三个协作件，都在 `data/agent/src/main/java/github/ponyhuang/gimi/data/agent/`：

1. **`AgentBuildSpec`** —— 不可变构建输入：模型 `selection`、`toolAccessMode`（`ALWAYS_AVAILABLE`/`ON_DEMAND`）、`reasoningEffort`、`thinkingEnabled`、`pluginRuntime` 快照、`instructionSuffix`、`a2uiSupported`。
2. **`AgentContribution` SPI** —— contribution 是自声明的 capability 拥有者：`id`、`revision()`（缓存键输入；无外部可变配置时返回 null）、`toolsets(spec)`（挂在 `LlmAgent.toolsets` 上的命名工具集——`ALWAYS_AVAILABLE` 下常驻，`ON_DEMAND` 下大多转为检索候选）、`tools(spec)`（直接附加的工具）、`candidateSources(spec)`（`ON_DEMAND` 下进 `ToolSearchToolset` 的检索候选源）、`subAgents(spec)`（模型经 `transfer_to_agent` 委派的非 LLM 子能力）、`toolCatalog(context)`（给推荐/能力目录等旁路消费的扁平枚举——与 agent 构建解耦）。`AgentContributionRegistry` 聚合 `Set<AgentContribution>`（Hilt `@IntoSet`），按 `id` 排序保证确定性，重复 id 直接失败。现有 contribution 类型（注册 id）：app function（`appfunctions`）、a2ui、sandbox（E2B）、local、mcp、official、skill、plugin、base（记忆/用户输入）、model catalog（仅 revision）、remote agent（仅 sub-agent）、delegate tasks（子任务）、direct phone（`direct_phone`，仅 sub-agent，后端报告可用时才挂载）。各自加载什么属于实现细节，需要时读对应类。
3. **`AgentFactory` + `AgentChatRunner`** —— 工厂解析模型（`AgentLLMModelFactory`，回退链：显式选择 → 聊天页选择 → 第一个启用服务；全无则报错并指向设置页），用 `AgentPrompts.defaultAssistantInstruction() + spec.instructionSuffix`、思考配置、聚合的工具/工具集/子代理以及全局 `RemoteAgentFailureRecovery` 回调构建 ADK `LlmAgent`。`ON_DEMAND` 模式下追加 `ToolSearchToolset`（ObjectBox + 向量检索，视为可重建缓存、永不迁移）。Runner 以 LRU 缓存至多 10 个 `SharedRuntime`，键为 `AgentKey(selection, toolAccessMode, reasoningEffort, thinkingEnabled, instructionSuffix, a2uiSupported, revision)`——其中 `revision` 是单一合并值：`AgentBuildConfigurationSnapshot` 在查缓存时捕获全部 contribution 的 `revision()` 输出，其 `pluginRuntime` 快照就是实际参与装配的内容——返回每回合的 `AgentChatExecution`。

**注册**：每个 contribution 在 `di/AgentContributionModule.kt` 一行 `@Binds @IntoSet`（subtasks 在自己模块绑定）。新增工具/配置源 = 实现 `AgentContribution` + 一行绑定，不改编排代码。这个抽象模块多绑定是既定可用的模式——下文「坑」里 `@Binds` 不聚合的问题指 data 模块里其他普通单例绑定。加完绑定后要验证 contribution 真的进了集合（如注册表重复 id 报错或工具目录输出）。

## 运行生命周期、确认与用户输入

- **租约**：每次运行（聊天、语音、系统助手）先取 `AgentRuntimeGate` 租约；会话级忙碌状态与任务阶段都在那里。停止语义：停止 = 拒绝待确认项；输入请求没有拒绝、保持挂起。
- **工具确认**走 ADK `adk_request_confirmation` 长时运行协议。`AgentChatExecution.respondToToolConfirmation` 必须收齐全部决定再恢复（ADK 1.2.0 怪癖：每个决定恢复一次 invocation、而不是收齐后恢复一次，会提前结束 invocation）。UI 侧由 `ChatRunLifecycleCoordinator` 排队 `pendingToolConfirmations`，支持经 `ToolApprovalRepository` 的「总是允许」与本回合自动批准。
- **启用/停用**：`LocalToolContribution.revision()` 跟踪 `ToolAuthorizationRepository.revision`，切换即重建缓存 agent；每请求过滤在工具集内部经 `ToolRunMetadata`（`RunConfig.customMetadata`）完成。
- **用户输入协议**（`conversation/UserInputToolProtocol.kt`）：`adk_request_input` → `{"input": ...}`，`get_user_choice` → `{"choice": ...}`；回复是带原 call id 的 role=user `FunctionResponse`（工具不重跑）。因 ADK 不会把恢复回放为事件，UI 在本地合成响应消息。名称真源：`domain/conversation` 的 `UserInputToolCallNames`。
- **持久化**：历史全部在 ADK `SessionService`（Room）。`retry=true` 恢复原保存的 invocation（用户事件不在磁盘上重复）。

## 语音模式与远程代理（简）

- **聊天模式语音**（`data/agent/voice/VoiceAgentConversation.kt`）把识别文本喂给与文本聊天**相同**的缓存运行时和会话——没有独立 agent。差异：每次通话取运行租约、工具确认按设计全自动批准、`adk_request_input`/`get_user_choice` 暂停到用户下次开口、文本增量流向 TTS、带朗读 `instructionSuffix` 且无 A2UI 能力（因此缓存键不同）。实时（Live-API）语音是独立子系统，见[实时语音系统](../voice/realtime-voice-system.md)。
- **远程代理（A2A）**（`data/agent/a2a/`）：`RemoteAgentContribution` 把已配置的远程 agent 暴露为 `subAgents()`（模型经 `transfer_to_agent` 委派）。卡片拉取：OkHttp、可取消、15 秒超时、禁止重试/重定向、大小上限、bearer 头。`ResilientRemoteAgent` 把远程失败转为非终局本地失败事件，让父回合继续。远程 agent 实例必须每次树装配新建——绝不复用已挂接的实例。

## 不变量

1. **领域纯净**：`:domain:conversation`（及所有领域模块）只依赖 Kotlin/coroutines；`ChatRunEvent`/`ChatAgentRepository` 是与 SDK 无关的接缝。ADK 类型止步于 `:data:agent`/`:data:conversation`。
2. **缓存键纪律**：构建级旋钮进 `AgentKey`；会话级旋钮（工具勾选、确认开关）经 `RunConfig.customMetadata` 进 `ToolRunMetadata`——绝不进缓存键。contribution 消费的任何配置必须反映到其 `revision()`，否则陈旧缓存 agent 会继续服务。
3. **事件映射一致性**：流式（`ChatRunEventMapper`）与历史重放（`EventMapper`）必须共用 id 规则：`Message.id = Event.id`，part id 按消息内 part 序号派生（thought 与 text 共用索引 0）。DAO 读出后绝不按时间戳重排（DAO 顺序 = 追加顺序 = 发出顺序）。只做逐 part 降级——一个坏事件绝不能清空整个会话。
4. **凭据绝不进 `ModelRuntimeMetadata`**（ADK 会把 custom metadata 合并进持久化事件）；凭据在每次工具执行时重新解析。
5. **JSON 原生工具载荷**：工具返回结构化 `Map<String, Any?>`，不是序列化字符串。
6. **取消自然传播**；`NonCancellable` 只用于持久化/收尾边界（A2UI 任务收尾、草稿清理、租约释放）。
7. **重试有界且只读**：`AgentToolRetryPolicy` 只覆盖只读工具（`list_skills`、`load_skill*`、`tool_search`）；执行类工具不进重试预算。

## 坑

- `di/AgentModule.kt` 是 `object` + `@Provides`（约 17 个单例）；runner 用 lambda 参数化而非构造注入。data 模块 Hilt 坑：优先 `object` + `@Provides`；data 模块的 `@Binds` 曾静默不聚合（陈旧 `kspCaches` 顽固）。
- ADK 1.2.0 确认批处理怪癖（见上）——「修」成逐批恢复会静默回归。
- 语音自动批准确认是文档化的设计决定，不是 bug。
- A2A 卡片拉取禁止重定向/重试（auth 头泄漏风险）；远程错误*事件*按异常处理，因 SDK 行为不一致。

## 改动清单

- 新工具/配置源 → 新 `AgentContribution` + 一行 `@Binds @IntoSet`；`id` 唯一；消费的配置变化必须反映到 `revision()`。
- 生命周期/确认改动 → 保持「收齐全部决定再恢复」；检查 `AgentRuntimeGate` 租约对称性（获取/释放，`NonCancellable` 释放）。
- 事件管线改动 → 流式与历史 mapper 同步改；已有映射一致性测试（`EventMapper*Test`、`ChatRunEventMapper*Test`）。
- 提示词改动 → `AgentPromptsTest` 守护默认指令；`instructionSuffix` 变化会产生新缓存条目。
- 缓存/装配改动 → 见 `AgentChatRunnerCacheTest`、`AgentFactoryToolAccessTest`、`AgentContributionRegistryTest`。
