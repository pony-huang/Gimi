简体中文 · [English](../../en/modelcatalog/model-services.md)

# 模型配置操作约束

审阅日期为 2026-11-14，面向开发人员。

本文是统一模型服务配置改造落地的**操作约束与新增指引**（2026-11-14 已实现）：术语与范围、领域模型规则、存储规则、适配分派规则，以及新增厂商、厂商工具、语音适配时必须执行的步骤。后续开发与新增按本文执行。

## 术语与范围

- **模型服务**：一套具有独立名称、用于接入所选模型的配置。同一厂商或兼容协议可以对应多套模型服务，各套分别管理凭据、连接和模型选择。
- **内置厂商模板**：用于创建模型服务的预定义厂商配置，描述应用已集成的能力及其默认接入设置。
- **服务凭据**：归属于一套模型服务、供其中一个或多个连接使用的访问凭据；连接具有不同访问要求时可用独立凭据。
- **服务连接**：模型服务中某个已集成接口的接入配置，包含协议、端点、凭据来源及必要访问参数。
- **LLM 模型**：为对话生成任务配置的模型，涵盖普通对话与快速模型用途。
- **语音模型**：为语音识别、音频理解、语音合成或实时语音对话配置的模型；一个模型可支持多种用途。
- **自定义 LLM 接入**：通过 OpenAI Chat Completions 或 Anthropic Messages 配置的第三方接入，包含连接设置和对话模型；范围排除厂商专属工具与语音能力。

## 领域模型约束

### 实例与模板身份分离

> 决策依据：共享单一厂商身份虽然便于分组，但无法支持同厂商多账号与第三方兼容接入；因此每套配置拥有独立身份与名称，模型选择与服务操作必须区分配置实例与厂商模板。


- 配置实例（`ModelServiceInstance`）持有独立稳定 ID；`templateId` 只是适配元数据（协议分派、厂商工具矩阵、语音适配、品牌图标）。**禁止**把实例 ID 当作厂商标识使用——品牌图标按模板 ID 查，实例 ID 查不到会静默回退占位图（已踩坑）。
- `ModelSelection = (instanceId, connectionId, modelId)`。默认引用、会话持久化、编码（`ModelSelectionCodec` 三段格式）都以实例 ID 开头；不同实例的同名模型互不混淆。
- 自定义 LLM 服务 `templateId == null`，只允许一条对话连接；厂商工具矩阵对它天然不可用，但 MCP/插件等通用工具不受影响。

### 连接与能力类别

- 一条 `ServiceConnection` 对应一个 `ServiceCapability`（Conversation / Recognition / Synthesis / Realtime），一一对应，不合并。
- 识别连接同时容纳专用转写（STT）与音频理解（ASR）模型；协议适配按模型声明的能力分派，实例不可只按整批判定。
- 双协议厂商（OpenAI 兼容 + Anthropic 兼容）的模板连接必须**成对给出两个协议的默认端点**（MiniMax / MiMo 曾漏配 Anthropic 路径，切换协议时端点会错误回落）。

### 凭据收敛

- `List<ModelServiceInstance>.resolveConnection(selection)`（domain 顶层扩展）是运行时与界面快照解析的**唯一真源**：停用、凭据为空、连接或模型不存在一律返回 null；多密钥取首个（逗号分隔惯例）。仓库实现与各消费方都委托它，不得自行解析。
- 凭据来源二选一：共享（实例 `sharedApiKey`）或连接级 `CredentialSource.Independent`。厂商工具的凭据归属实例的**对话连接**，跨模型工具在执行期按来源实例解析。

### 草稿与启停

- `ModelServiceDraft` 不携带启停状态：启停是独立提交的操作，保存草稿时仓库读取存储中的最新启用状态，防止覆盖即时启停。
- 保存校验（`hasValidConfiguration`）：至少一条连接已选模型；已选模型的连接结构完整（协议、端点、独立凭据非空）；业务空间 ID 仅在模板支持时按主机名安全字符校验。未选模型的能力连接不阻碍保存。
- `EndpointPolicy.VendorManaged` 连接的端点在保存期回贴模板默认值，用户不可编辑。
- 保存成功后 VM 必须**原地收编**实例 ID 与归一化后的已保存实例，禁止向导航栈压新的详情条目（曾导致返回叠层、点击失效）；效果通道用 `MutableSharedFlow(replay=0)`，禁止用会重放的 Channel。

## 存储约束

- Room（`model_template`，版本 3）只承载公共模板：厂商模板、能力连接定义、内置候选目录。启动时执行播种（seed）、删除已下线模板（prune）、内置目录合并（upgrade），三者幂等。
- 实例、连接凭据、共享/独立来源、已选模型与默认引用保存在单一 Keystore 加密快照（`model_service_configuration_v1`）；每次变更**整体原子提交**，持久化成功后才发布状态，失败保留原状态与草稿。
- 默认引用（助手/快速/识别/合成/实时 + 音色）为同文件内的未加密键。删除实例必须在**同一提交内**清理引用它的默认分配与绑定音色（朗读音色重置为未配置，通话音色清除覆盖）。
- 朗读（级联 TTS）音色是单一全局键，**空串即未配置**。切换合成模型时清空音色，音色不会比它所属的服务活得更久。默认模型页在音色列表加载后把空值音色自动落成该模型的第一个音色（只写空值，不改写用户已配置的音色——音色集不是封闭集合，基础音色与复刻音色都不在表里）。请求期合成仓储再做一次兜底解析：已配置的优先，未配置回落到 `VoiceCatalogResolver.builtInDefaultVoice`（服务内置表的第一个音色，千问按模型收窄），无内置表的服务（openaicompatible 系）按空值交给服务端默认。不得把厂商专属占位音色发给其他厂商（MiniMax 会报 2054 voice id not exist，通话中途失败）。
- 按仓库开发期规则**不写历史迁移**：改配置格式就换 prefs 文件名版本（vN → vN+1）；Room 版本 +1，`fallbackToDestructiveMigration`。默认引用键名保持稳定。

## 适配与运行时分派

- 聊天协议分派收敛在 `AgentLLMModelFactory.createModel` 的 `when(baseType)`；新增协议需要：`ApiProtocol` 加枚举 → 工厂加分支 → 模板连接 `protocols`/`defaultEndpoints` 放行。
- 语音适配按**模板 ID** 注册（`CapabilityAdapter.serviceIds`）；`VoiceCapabilityRegistry` 解析不到即显式失败，不得借用其它厂商协议兜底。连接测试：对话连接走模型目录探测，语音连接按模板握手探测（未实现探测的报告 Unsupported）。
- Realtime 装配按 `templateId` 分派（现有分支：qwen / openai），未接入原生适配器的模板不得出现在 Realtime 候选里。
- 厂商工具矩阵在 `OfficialToolSupport.all`，按 `templateId + protocol + 模型家族` 匹配；`ModelConfig` / `ModelRuntimeMetadata` 携带 `templateId` 并经 RunConfig metadata（`gimi.model.template_id`）往返；DeepSeek Anthropic 参考工具加载按模板身份加官方端点判定。
- 端点策略：`VendorManaged`（百炼 WebSocket、OpenAI Realtime 等厂商自有端点）由模板给定；其余沿用用户/模板配置端点。语音请求基址统一走 `speechRequestBaseUrl`（百炼业务空间 ID 参与专属域名改写）。

## 操作指引

### 新增厂商模板

1. `data/modelcatalog` 的 `ModelServiceTemplates` 追加模板：能力连接、`catalog` 候选目录；**双协议厂商必须同时给 `additionalEndpoints` 的 Anthropic 地址**。
2. `core/designsystem` 的 `LLMModelServiceIcon` 映射追加模板 ID → 图标；缺失会静默回退占位图（Qwen 曾踩坑）。
3. 有厂商工具则在 domain `OfficialToolSupport.all` 加 `OfficialToolSpec`（见下节）。
4. 厂商工具行文案在 `feature/modelsettings/detail/OfficialToolCopy.kt` 按 toolId 补充。
5. 语音能力见「新增语音适配」。
6. `feature/modelsettings` 的字符串不需要动（模板名与能力摘要由模板数据驱动）。

### 新增厂商工具

1. domain `OfficialToolSupport.all` 追加 `OfficialToolSpec`：`templateId`、允许 `protocols`、`binding`（ProviderDeclaration / ProviderNative / LocalFunctions）、`canBeUsedAcrossModels`、可选模型家族限制。
2. `data/agent` 的 `OfficialToolFactory` 为 LocalFunctions 加构造分支：凭据取来源实例的对话连接生效值，禁止写死厂商 key。
3. `data/modelaccess` 实现独立 API / 工具类。
4. `feature/modelsettings` 的 `OfficialToolCopy` 按 toolId 补名称与作用描述（界面不显示目录英文名与厂商前缀）。

### 新增语音适配

1. `data/modelaccess` 实现 `CapabilityAdapter`（`serviceIds` = 模板 ID、`endpointStrategy`、按能力声明）及协议实现。
2. `data/modelaccess` 的 `ModelAccessModule` 以 `@IntoSet` 注册。
3. `app` 的 `VoiceCapabilityCatalogConsistencyTest` 手工镜像清单同步——模板目录里声明了语音能力的模型必须都能解析到适配器，否则该测试失败。

### 修改配置存储

1. 换加密快照 prefs 文件名版本，不写迁移；默认引用键名保持。
2. Room 结构变更时版本 +1（当前 3），依赖 destructive 重建；模板播种/清理逻辑保持幂等。
3. `ModelServiceConfigurationPersistenceTest` 必须覆盖：提交失败不发布、启停与草稿保存并发一致、删除清理默认引用与音色、损坏数据回落空配置。
