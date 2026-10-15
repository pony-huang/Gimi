# 模块结构评估与优化

评估日期：2026-10-04。范围：Gradle 模块依赖、生产 Kotlin 源码体量、官方工具目录与 Agent 运行时链路。没有改变界面、存储格式或模型调用协议。

## 结论

当前项目的主要问题是少数模块和类内部职责集中，而不是所有模块都需要合并。`settings.gradle.kts` 声明 62 个模块，domain/data/feature 的能力分层已经建立，适合保留。第一轮通过现有模块之间的职责迁移解决实际耦合，不增加新的业务模块。

以下是改动前的生产 Kotlin 源码统计，不含测试、生成代码、资源和构建脚本；行数用于定位维护热点，不等同于性能结论。

| 模块/文件 | 体量 | 意义 |
| --- | ---: | --- |
| `data:agent` | 97 文件 / 11,718 行 | 运行时、模型协议、工具执行、目录发现及检索集中在一起 |
| `feature:chat` | 40 文件 / 11,328 行 | 会话生命周期、消息、语音、工具配置及多个 UI 入口集中 |
| `app` | 14 文件 / 1,401 行 | 组合根总体保持较小，依赖较多符合其装配职责 |
| `data:modelcatalog` | 7 文件 / 1,358 行 | 模型服务配置、凭据、远程模型目录已有明确归属 |
| `ChatViewModel.kt` | 1,688 行 | 运行 lease、发送/重试/编辑、切换会话、语音及工具目录等职责交织 |
| `ChatAddToChatSheet.kt` | 1,189 行 | 功能入口集中，后续适合按交互内容拆分组件 |
| `ChatScreen.kt` | 1,123 行 | 布局与交互编排复杂，需要保持状态与副作用边界 |
| `LLMModelProviderStore.kt` | 552 行 | 配置存储、目录操作、模型选择与 DTO 映射集中 |

## 现有优点

- 能力域分层明确。生产依赖检查未发现 feature 互相依赖、domain 依赖 data、data 互相直接依赖或模块依赖环。
- `app` 主要负责装配，Hilt 在 data 中绑定 domain 接口；已有能力可以独立测试，不需要通过 app 获取隐式依赖。
- 已有 Agent contribution、插件 API 和工具候选来源等扩展点，工具按请求配置解析，会话开关不必触发整个 Agent 重建。
- 有官方工具、模型协议、会话状态等现有行为测试，以及统一的 Gradle convention plugins、依赖版本目录和 CI。
- 狭窄的 domain 契约与 core 基础设施限制了 SDK 和 Android 类型扩散。小能力模块即使文件少，也可能有明确的隔离价值。

## 主要不足与优先级

| 优先级 | 问题 | 针对性调整 | 本轮状态 |
| --- | --- | --- | --- |
| P1 | 官方工具目录实现依赖 Agent 注册表，支持规则和 ADK 构造闭包混在一起 | 纯声明与规则归 modelcatalog，执行工厂归 agent | 已实施 |
| P1 | Kimi 动态目录包含 ADK Schema，目录查询和执行共享关系依靠运行时实现 | 原始 JSON Schema + domain 声明源接口 + 单例缓存 | 已实施 |
| P1 | 模块规则主要依靠文档和人工审查，后续容易出现反向依赖 | 在 CI 中检查生产依赖、标准包名的直接依赖、禁止导入与依赖环 | 已实施 |
| P1 | ChatViewModel 同时协调会话运行、失败恢复、语音和工具配置 | 按状态所有权提取内部协作者；先补状态转换特征测试 | 工具配置与失败恢复已提取，运行生命周期待整理 |
| P2 | 工具文件检索基础设施与 Agent 工具适配集中 | 找到真实的第二消费者或独立生命周期后，再抽出检索能力；SDK 适配继续留 agent | 保留现有模块 |
| P2 | modelcatalog 存储实现体量偏大 | 后续按配置读写、选择解析和远程目录刷新拆内部实现，不新增空接口或一对一转发层 | 后续独立改动 |
| P2 | 多个聊天 UI 文件超过千行 | 按稳定的交互职责拆组件，保持单一 UiState 与 Route 副作用边界 | 未改 UI |
| P3 | 部分能力只有少量文件，但每个能力都有 Gradle 模块 | 先测配置时间、增量构建时间、变更传播范围，再决定是否合并；按文件数合并会损失隔离价值 | 未做性能推断 |

## 本轮官方工具边界

```text
feature:chat ──────────────┐
                          v
                  domain:modelcatalog
                  - OfficialToolSpec / OfficialToolBinding：纯声明
                  - OfficialToolSupport：唯一厂商支持矩阵与规则
                  - OfficialToolFunctionCatalog：目录查询契约
                  - FormulaDeclaration / KimiFormulaSource：原始声明与共享加载契约
                          ^
                          |
                  data:modelcatalog
                  - DefaultOfficialToolFunctionCatalog：目录实现
                  - KimiFormulaManifest / KimiFormulaCache：网络目录与缓存

                  data:agent -> domain:modelcatalog
                  - OfficialToolFactory：声明转 ADK 工具实例
                  - KimiFormulaSchema：JSON Schema 转 ADK Schema
                  - DefaultOfficialToolset：请求期、会话函数过滤、ON_DEMAND
                  - 厂商执行 API、协议 wire 转换与 Agent contribution

app：Hilt 装配上述实现，data:agent 不直接依赖 data:modelcatalog。
```

Agent 单测用 `testImplementation(data:modelcatalog)` 验证真实目录与 ADK 工厂的集成边界，该依赖不进入生产图。旧注册表与 Agent 中的目录 DI 绑定已删除，没有保留转发兼容层。

本轮保持原有服务/协议/模型家族限制、跨模型 MiniMax 生图、函数勾选、原生声明和 ON_DEMAND 行为。同时修正动态目录加载的两个失败边界：取消继续传播；全部端点失败不再被当作成功空目录缓存五分钟，恢复后可以立即重试。部分端点失败仍保留其他端点成功的声明。

## 防止继续膨胀

- 新增官方工具时，在 `OfficialToolSupport` 定义目录，在 `OfficialToolFactory` 定义执行适配。已有测试检查每条目录绑定都能构造工具，避免两者漏接。
- 将“目录发现”和“执行”区分为不同职责，目录不得保存 `BaseTool`、SDK Schema 或工具构造闭包。
- 新建模块需要明确的数据/状态所有权、真实消费者或生命周期收益。仅为减少文件行数，优先拆内部类与文件。
- domain 接口应形成业务或技术隔离边界，不为每个函数机械增加 UseCase、Repository 或 Manager。
- 每次结构调整完成一个可回滚的能力闭环，并在原所属模块保留可观察行为测试。

边界检查命令：

```bash
python3 -B -m unittest discover -s .github/scripts -p 'test_*.py'
python3 -B .github/scripts/check_architecture.py
```

检查器解析本仓库字面量形式的 Kotlin Gradle 依赖声明，检查非测试 Kotlin source set 的导入。它不解析动态 Gradle DSL、SDK 的传递依赖、Java 源码或跨模块运行时反射，不能代替 Gradle 编译和人工职责审查。

## 后续建议

下一步优先处理 `ChatViewModel`，从工具配置与目录加载协作者开始，再独立处理发送/失败恢复与会话运行状态。运行 token、lease 和切换会话的状态必须有唯一所有者，避免拆分后通过多个可变 StateFlow 同步同一状态。第一步可以保持现有界面与 UiState，不需要修改布局。

此后再评估 Agent 的检索基础设施与 modelcatalog 存储内部拆分。尚未测量构建速度、内存或运行时性能，因此本轮不宣称相应性能改善。

## 本轮验证结果

- `domain:modelcatalog:test`：6 个测试通过。
- `data:modelcatalog:testDebugUnitTest`：40 个测试通过。
- `data:agent:testDebugUnitTest`：242 个测试通过。
- `app:assembleDebug`：通过，包括完整应用编译、Hilt 生成与 APK 装配。
- 架构检查器：7 个自测通过，62 个模块的源码边界检查通过。
- `git diff --check`：通过。

上述验证使用已有 Android SDK 与 Gradle 离线缓存完成，没有调用真实模型服务。未做设备验证或构建性能基准测试。

## 第二轮：聊天工具配置协调

`ChatToolConfigurationCoordinator` 承接官方工具目录展示、函数异步加载、默认 marker 展开、函数勾选、MCP 开关、推理深度和会话配置持久化。ViewModel 的 action 分发、模型变化与 runtime 发布直接调用该协作者，没有保留旧函数转发层。

- `ChatViewModel.kt` 从 1,688 行减少到 1,475 行，协作者为 256 行。这次调整用于集中一个职责，并非减少总源码量。
- `ChatUiState` 与 `ChatSessionRuntime` 仍是唯一状态源；协作者使用 ViewModel 的状态、作用域和 runtime 回调，没有自己的 StateFlow 或会话缓存。
- 目录继续跨会话复用，配置写入继续使用操作发起时捕获的 session/runtime。加载完成时，默认 marker 按原有行为在当前可见会话展开。
- 没有改变 UI、domain 接口、ViewModel 构造依赖类型、存储格式、Agent 运行 token 或 lease 管理，也没有新建 Gradle 模块。

拆分前先增加 6 个 ViewModel 特征测试，覆盖默认 marker 持久化、目录复用、失败显式重试、函数勾选、写入失败和目录加载期间切换会话。拆分后完整聊天模块的 100 个 JVM 测试通过，其中 ViewModel 特征测试 55 个；聊天模块编译、生产边界检查和 diff 检查通过。未进行设备验证。

下一步处理会话发送与失败恢复的内部协作者。运行 token、lease 与任务生命周期需要继续集中在一个所有者中，先保留现有恢复状态与事件边界再拆分，不通过多份可变状态互相同步。

## 第三轮：发送准备与失败轮恢复

发送准备作为跨仓库流程归入 `domain:conversation` 的 `PrepareChatSendUseCase`：重新读取工具配置、按内容版本选择历史、归档输入并创建执行上下文。`ChatHistorySnapshot` 与 `PreparedChatSend` 只携带领域数据和执行接口，不依赖 Android、ViewModel 或 ADK 类型。历史快照在配置读取后获取，保持原有挂起期间的内存历史语义。

附件组合、模型能力、MIME、单文件大小与 50 MiB 文档总量校验归入 `ValidateChatAttachmentsUseCase`。领域仅返回校验原因，feature 通过 `ChatAttachmentNotices` 映射到现有提示类型，没有增加界面文案。

`ChatTurnRecoveryCoordinator` 负责失败轮快照、编辑草稿、重试和重复执行确认。确认后的请求仍回到 ViewModel 的 `startSend`，原样重试与编辑发送没有另建执行链路。ViewModel 继续统一持有运行 token、lease、并行任务限制、导航版本、接管回执以及接受后草稿清理。

- `ChatViewModel.kt` 从上一轮的 1,475 行减少到 1,271 行；失败恢复协作者为 214 行。没有新增 Gradle 模块或第二份 UI/runtime 状态。
- 五个新增特征测试先在拆分前通过，覆盖已执行工具后的重试/编辑确认、晚到编辑草稿清理、混合类别与文档总量限制。
- 修复编辑草稿加载/清理吞取消异常的问题，使用既有 cancellation-aware 恢复边界；新增特征测试确认取消后不会回填输入框或发出失败提示。
- 新增 11 个 domain 测试，验证历史版本选择、配置读取顺序、准备失败/取消传播和附件限制的边界值。

验证：`domain:conversation:test` 的 39 个测试、`feature:chat:testDebugUnitTest` 的 106 个测试、完整应用 `app:assembleDebug`、模块边界检查与 `git diff --check` 均通过。没有进行设备验证或性能基准测试。

后续优先整理会话运行生命周期，使 lease 获取/释放、运行 token、暂停恢复和收尾继续由同一个所有者协调；避免将确认恢复与普通发送各自拆成独立任务执行器。


## 第四轮：会话运行生命周期

`ChatRunLifecycleCoordinator` 集中普通发送、工具确认恢复、用户输入恢复的运行入口，并统一持有 token 写入、Job 登记、lease 获取/释放、暂停队列处理、停止与任务收尾。事件归约仍由 `AgentEventReducer` 负责，失败轮快照仍由 `ChatTurnRecoveryCoordinator` 负责；ViewModel 保留导航、提交接管、并行数量限制、历史加载及语音播放策略。

- `ChatViewModel.kt` 从 1,271 行减少到 971 行。没有新增 Gradle 模块、注入接口或状态副本；协作者使用同一份 `ChatSessionRuntime`、UI 状态及 ViewModel 作用域。
- 暂停保留执行上下文与 lease；确认或输入恢复先等待旧 Job 结束，再继续原执行。只有持有当前 token 的任务可以收尾。主动取消仍等待旧 Job 真正完成才释放 lease。
- 停止工具确认沿用拒绝协议；停止挂起输入请求仍保留请求，后续答复可以恢复。普通停止同步结束部分消息的流式显示并保留可重试轮。
- 原有 106 个聊天测试在提取后通过。新增连续工具确认 lease 测试，并增强输入恢复测试，验证等待期间停止不释放 lease，最终答复只释放一次。

后续优先审视 `data:agent` 内执行适配与工具组装的职责边界，以及剩余会话导航/历史缓存协调的复杂度；拆分依据是依赖和状态所有权，不以文件行数或模块数量作为独立目标。

本轮最终验证：`feature:chat:testDebugUnitTest` 的 107 个 JVM 测试全部通过，`app:assembleDebug` 成功；62 个模块的架构边界检查及 `git diff --check` 通过。未进行设备验证或性能基准测试。


## 第五轮：共享运行时与单轮执行适配

审视 `data:agent` 后，工具组装已有 `AgentContributionRegistry` 与能力贡献方边界，`AgentFactory` 仅约百行，暂不增加装配层。优先将 `AgentChatRunner` 内嵌的单轮执行句柄独立为 `AgentChatExecution`，直接移动实际协议实现，没有保留嵌套转发类或新增接口。

- `AgentChatRunner` 负责构建配置快照、共享 Agent/Runner 构建、互斥 LRU 缓存及创建执行句柄，源码从 314 行降至 158 行；执行适配为 160 行。
- `AgentChatExecution` 负责用户消息/附件的 ADK 编码、失败轮 invocation 回退、工具确认与输入 FunctionResponse 恢复、SSE/IO 流配置，以及 MobileUse 执行注册和不可取消的结束清理。
- 执行句柄直接持有创建时的 Runner 与工具元数据；恢复不读取全局配置或访问 LRU。缓存淘汰不会改变等待中的执行，工具开关仍作为每轮 metadata 传递，不增加缓存键维度。
- `AdkChatAgentRepository` 继续负责领域接口及事件映射。SDK 执行逻辑仍归属 `data:agent`，没有迁入 modelcatalog，也没有新增 Gradle 模块、注入绑定或跨 data 生产依赖。

提取前增加并通过三个协议特征测试：回退必须先于新 invocation、回退失败保留原因且不启动新 invocation、取消回退原样传播。既有用例覆盖同一 Runner/metadata 恢复、LRU 淘汰后恢复以及 MobileUse 正常/失败/取消清理。

后续建议检查工具候选源在构建、搜索与索引之间的配置快照一致性，再决定是否需要进一步拆分；不继续按文件行数机械拆类。

本轮最终验证：`data:agent:testDebugUnitTest` 的 245 个 JVM 测试全部通过，完整应用 `app:assembleDebug` 成功；62 个模块的架构边界检查与 `git diff --check` 通过。未进行设备验证或性能基准测试。

## 第六轮：工具发现失败与搜索目录一致性

检查搜索候选源与向量索引后，保留已有两阶段设计：完整目录用于索引，当前启用目录用于匹配后的过滤；持久化工具名在每次模型请求前也重新过滤。正常目录缓存与每轮开关/授权状态分开，不需要为开关变化重建完整目录。

发现并修复 MCP 来源的失败缓存问题：`McpServerSource` 原先把发现异常降级为空列表，`ToolSearchToolset` 随即将其缓存为成功空目录。同一 Agent 后续即使发现恢复，向量目录也可能一直缺少该来源，并且搜索结果没有来源失败提示。

现在候选源将异常交给搜索层统一处理。搜索层继续隔离单来源失败、返回脱敏 `source_errors`，成功目录才写入缓存；失败或取消不写入缓存，后续搜索可以重新发现。真正成功的空目录仍可缓存。没有新增模块、状态副本或目录抽象。

新增三个测试：

- 真实 `McpServerSource` 失败时仍可搜索其他来源、失败提示不泄露地址或 token，恢复后工具重新进入索引和结果。修复前该测试失败，复现来源错误被隐藏的问题。
- 开关变化后，重复搜索和持久化选择均使用当前启用状态过滤；完整目录保持缓存，重新启用后可恢复工具。
- MCP 发现取消原样传播，不污染目录缓存，后续调用仍可成功。

本轮关注的是发现失败恢复与配置过滤，没有改变向量算法、预算规则、持久化工具名格式或授权规则，也未声称覆盖所有远端动态目录变更。

本轮最终验证：`data:agent:testDebugUnitTest` 的 248 个 JVM 测试全部通过，`app:assembleDebug` 成功；62 个模块的架构边界检查与 `git diff --check` 通过。未进行设备验证或实时 MCP 服务验证。

下一步建议回到聊天会话导航与历史加载，审视缓存失效、会话切换和外部写入的状态协调；优先明确状态所有权与竞态边界，再判断是否拆分协作者。

## 第七轮：会话导航与历史刷新协调

`ChatSessionNavigationCoordinator` 集中启动恢复、新建会话、会话切换、历史版本刷新，以及对应的导航版本、加载 Job 和加载 token。发送入口只读取导航版本/加载状态来判断提交是否仍有效；运行生命周期在收尾时回调同一历史刷新入口。

- `ChatViewModel.kt` 从 971 行减少到 781 行；导航协作者为 224 行。没有新增 Gradle 模块、领域接口、注入依赖类型或第二份会话消息缓存。
- `ChatSessionRuntime` 与 `ChatUiState` 继续由 ViewModel 持有，消息展示/状态发布沿用同一入口；导航协作者只拥有导航和异步加载控制状态。
- 保留加载 token 守卫：旧会话读取即使忽略取消并晚到，也不能覆盖新会话或清掉新加载的状态。
- 保留运行 token 守卫：历史刷新挂起期间即使新一轮已完成，也不能用旧历史覆盖新结果。
- 运行中会话仍显示内存流并重建显示通道；空闲会话切入仍读取权威历史。外部写入版本在运行结束后刷新，加载期间版本再次变化继续读取，不把旧快照确认成新版本。

拆分前新增两个竞态特征测试并通过：不可取消的旧历史读取晚到时保持最新导航结果，以及旧刷新读取晚到时不能覆盖期间完成的新轮。既有测试继续覆盖外部版本推进、后台完成后的权威历史加载、多会话流互不污染、输入/工具确认跨会话保留。

本轮按职责集中加载控制，不改变消息持久化、UI、错误提示或后台运行语义。


本轮最终验证：`feature:chat:testDebugUnitTest` 的 109 个 JVM 测试全部通过，`app:assembleDebug` 成功；62 个模块的架构边界检查与 `git diff --check` 通过。未进行设备验证。

下一阶段优先检查模块依赖与构建成本，寻找可以移除的依赖和重复配置；当前聊天职责已有明确分工，后续不继续以减少 ViewModel 行数作为拆分目标。

## 第八轮：编译插件与依赖清理

审查模块源码、注解及依赖声明后，移除能确认无本地消费者的构建配置，保留实际使用的 JSON 运行库和跨模块契约。

| 范围 | 清理内容 | 判断依据 |
| --- | --- | --- |
| `core:testing`、`data:conversation`、`data:mcp`、`feature:assistant` | 移除 Kotlin serialization 编译插件 | 各模块所有源集没有本地 `@Serializable` 类型；消费其他模块生成的 serializer 不需要本地编译插件 |
| `data:agent` | 移除 Room runtime、ktx、compiler、testing 四条声明 | 模块没有 Room 类型引用、数据库、DAO 或实体；持久化会话的 Room 实现在 `data:conversation` |
| `data:agent` | 移除直接 Gson 依赖 | 模块没有 Gson 类型引用；保留 SDK 自身声明的传递依赖 |
| `data:modelcatalog` | 移除 OpenAI Java SDK 依赖 | 模型列表通过 OkHttp/JSON 获取；OpenAI 模型推理客户端仍归属 `data:agent` |

本轮共清理 4 个编译插件应用和 6 条库/处理器依赖声明，没有合并模块、修改 UI 或业务协议。Compose 公共配置虽有重复，但现有 convention 主要负责构建能力，暂不把不同 feature 的依赖集合整体塞入公共插件。

清理 Room 编译器后，ADK 处理器的 KotlinPoet 解析版本发生变化；首次离线验证因该版本未缓存而失败，改为联网解析验证。依赖版本变化需要完整编译与 APK 验证，不能只按源码无引用判断完成。

没有进行可比的构建性能或 APK 体积基准。编译插件/处理器数量减少是配置变化，不等价于已经证明构建提速或安装包变小；应用其他模块仍使用 OpenAI/Room。

最终验证：受影响模块的 Gradle 测试任务与 `app:assembleDebug` 成功；`data:conversation` 47、`data:mcp` 8、`data:modelcatalog` 40、`data:agent` 248，共 343 个 JVM 测试通过。`core:testing` 与 `feature:assistant` 没有本地测试，通过模块编译及应用集成编译验证。62 个模块的架构检查与 `git diff --check` 通过，未进行设备验证。

下一步应建立可重复的构建/运行性能基线，再根据测量选择优化对象；继续减少模块数或拆文件无法单独证明改善了构建成本。

## 第九轮：可重复构建性能基线

新增 `.github/scripts/profile_build.py`，测量预热后的无改动 APK 构建及聊天私有实现单文件增量构建，保存墙钟耗时、配置缓存、任务状态与 Gradle HTML profile。增量探针在 `finally` 中恢复源码并重新构建 APK；保留原始源码备份，检测并发修改，避免覆盖其他工作。

正式测量各三次：无改动中位数 2.820 秒（2.615–2.863），聊天增量中位数 9.709 秒（8.691–12.122）。主要增量成本来自聊天 KSP、Kotlin 编译及 APK 打包，domain/data 与 app Kotlin 编译未重新执行。没有重构前的对应基线，不能推导优化提速比例。

结果、环境、复现命令和限制见 `docs/build-performance-baseline.md`。新增三个脚本测试验证任务状态区分、profile 保存/耗时排序与缺少 profile 的处理；连同架构脚本测试共 10 个测试通过。完成正式基线及一次单轮脚本端到端验证，源码恢复哈希一致，恢复后的 APK 构建成功。架构检查及 `git diff --check` 通过。没有运行设备性能测试。

## 第十轮：Hilt 生成边界与内部常量 ABI

测量确认聊天 KSP 会随源文件变更执行，但本次实现探针不改变 22 个生成产物的内容。没有将 ViewModel 注入拆入新模块，也没有关闭 KSP/Hilt。Hilt 2.60 的聚合任务默认已启用，现有 ViewModel 私有实现变更不会引发 app 的 KSP/Kotlin 重编译。

对无注解协作者的对照探针发现，私有 companion 中的非 private const 日志标签会在 JVM 外层类上暴露 public 字段，值变更因此向 app 传播。将 `ChatRunLifecycleCoordinator`、`ChatSessionNavigationCoordinator`、`ChatTurnRecoveryCoordinator` 的日志常量显式设为 private，保留原值及业务行为，缩小生成类的 ABI。

同样诊断参数下，生命周期探针修复前执行 13 个任务，修复后三次均为 9 个；app 的 KSP、Kotlin 编译、Hilt 类收集和类变换不再重跑。详细样本及限制见 `docs/build-performance-baseline.md`。测量脚本补充探针选择、KSP 生成文件哈希与变化记录；新增哈希测试验证时间戳无关、内容修改及文件增删检测。

最终验证：109 个聊天 JVM 测试、11 个脚本测试通过，`app:assembleDebug` 成功；JVM 字段核对为 `private static final`，探针源码恢复字节一致，62 个模块的架构检查与 `git diff --check` 通过。没有进行设备验证或测量稳定提速比例。

## 合并 main：SDK 原执行重试

合并 `origin/main` 的 `4db1e6dc` 后，当前行为以 main 的重试简化为准：失败/停止轮只提供重试，不再编辑会话事件或要求重复执行二次确认。`ChatTurnRecoveryCoordinator` 仅保留失败快照与重试入口，导航不再处理编辑草稿。历史阶段记录中的编辑/rewind 设计已被此次 main 变更替代。

保留本分支职责边界：将 main 的 invocation 恢复和 SDK 错误事件处理迁入独立 `AgentChatExecution`，`AgentChatRunner` 继续只管理共享运行时；`PrepareChatSendUseCase` 与生命周期协作者适配新的 retry Boolean 契约。保留 main 的异常、取消、SDK 错误后恢复原 invocation 的测试，移除已退休的编辑/rewind 测试；历史刷新与会话切换竞态测试继续保留。

合并验证：37 个 domain:conversation、47 个 data:conversation、247 个 data:agent、102 个 feature:chat 测试，共 433 个 JVM 测试通过；11 个脚本测试、完整 `app:assembleDebug`、62 模块架构检查和 diff 检查通过。未进行设备验证。
