package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * 服务能力类别；一条能力连接对应一个类别。
 *
 * 识别连接同时容纳专用转写（[ModelCapability.STT]）与音频理解（[ModelCapability.ASR]）
 * 两类模型，协议适配按模型声明的能力分派。
 */
enum class ServiceCapability { Conversation, Recognition, Synthesis, Realtime }

/** 凭据来源：共享服务级凭据，或连接级独立覆盖。 */
sealed interface CredentialSource {
    /** 使用实例的共享凭据（[ModelServiceInstance.sharedApiKey]）。 */
    data object Shared : CredentialSource

    /** 连接级独立凭据覆盖；与共享凭据互不影响。 */
    data class Independent(val apiKey: String) : CredentialSource
}

/**
 * 连接端点策略。
 *
 * - [VendorManaged]：官方端点由模板给定，用户不可编辑（如百炼 WebSocket、OpenAI Realtime）。
 * - [UserConfigured]：用户可编辑端点，模板提供默认值。
 */
enum class EndpointPolicy { VendorManaged, UserConfigured }

/**
 * 模型服务实例：用户创建的一套独立命名配置。
 *
 * 实例身份独立于厂商模板身份；同一模板可以派生任意多套实例，各自保存凭据、
 * 连接与模型选择。[templateId] 为 null 表示自定义 LLM 服务（仅对话连接）。
 *
 * @property id 实例稳定 ID，持久化与默认引用都以它为准。
 * @property name 用户命名；改名不影响实例身份与默认引用。
 * @property templateId 派生自的内置模板 ID；null 表示自定义 LLM 服务。
 * @property isEnabled 服务总开关；停用保留配置与默认引用，仅标记不可用。
 * @property isOfficialToolsEnabled 是否允许该服务的厂商工具参与会话。
 * @property sharedApiKey 服务级共享凭据；连接未覆盖时统一使用。
 * @property connections 该实例的能力连接集合；每条连接对应一个 [ServiceCapability]。
 */
data class ModelServiceInstance(
    val id: String,
    val name: String,
    val templateId: String?,
    val isEnabled: Boolean,
    val isOfficialToolsEnabled: Boolean,
    val sharedApiKey: String,
    val connections: List<ServiceConnection>,
) {
    /** 按稳定 ID 取连接；不存在时返回 null。 */
    fun connection(connectionId: String): ServiceConnection? =
        connections.firstOrNull { it.id == connectionId }

    /** 按能力类别取连接；模板集成决定哪些类别存在。 */
    fun connection(capability: ServiceCapability): ServiceConnection? =
        connections.firstOrNull { it.capability == capability }

    /** 连接的生效凭据：连接级覆盖优先，回落到共享凭据。 */
    fun effectiveApiKey(connection: ServiceConnection): String = when (val source = connection.credential) {
        CredentialSource.Shared -> sharedApiKey
        is CredentialSource.Independent -> source.apiKey
    }

    /** 对话连接；官方工具凭据归属与跨模型工具调用都以它为准。 */
    val conversationConnection: ServiceConnection?
        get() = connection(ServiceCapability.Conversation)

    /** 按选择定位连接中的模型；实例、连接或模型不存在时返回 null。 */
    fun findModel(selection: ModelSelection): Model? =
        connection(selection.connectionId)?.models?.firstOrNull { it.id == selection.modelId }

    /**
     * 服务能否为会话提供厂商工具：服务启用、工具偏好开启且对话连接凭据非空。
     * 厂商工具的凭据始终归属实例的对话连接，与来源模板无关。
     */
    fun canProvideOfficialTools(): Boolean =
        isEnabled && isOfficialToolsEnabled && conversationConnection?.let(::effectiveApiKey).orEmpty().isNotBlank()
}

/**
 * 一条能力连接：能力类别、协议、端点、凭据来源与已选模型。
 *
 * 连接通过稳定 [id] 归属实例；模型选择引用实例 ID、连接 ID 与模型 ID。
 *
 * @property id 连接稳定 ID；编辑草稿沿用既有 ID，避免默认引用悬空。
 * @property capability 连接承载的能力类别。
 * @property protocol 接入协议；同一实例不同连接可使用不同协议。
 * @property endpoint 配置的端点；[EndpointPolicy.VendorManaged] 连接保存模板给定值。
 * @property credential 凭据来源：共享或独立覆盖。
 * @property workspaceId 可选业务空间 ID；仅支持业务空间的模板连接消费（百炼）。
 * @property models 该连接已选模型；保存服务要求至少一条连接已选模型。
 */
data class ServiceConnection(
    val id: String,
    val capability: ServiceCapability,
    val protocol: ApiProtocol,
    val endpoint: String,
    val credential: CredentialSource = CredentialSource.Shared,
    val workspaceId: String? = null,
    val models: List<Model> = emptyList(),
)

/**
 * 服务编辑草稿。
 *
 * 启停是独立分组并单独提交的操作，不进入草稿：仓库保存草稿时读取存储中的最新
 * 启用状态，防止编辑保存覆盖刚发生的启停。新建实例时 [instanceId] 为 null，
 * 保存时由仓库分配稳定 ID。
 *
 * @property instanceId 编辑目标实例 ID；null 表示尚未落库的新实例。
 * @property templateId 派生模板 ID；null 表示自定义 LLM 服务。创建后不可变更。
 * @property name 用户命名。
 * @property isOfficialToolsEnabled 厂商工具偏好；无集成工具的服务不展示该开关。
 * @property sharedApiKey 服务级共享凭据。
 * @property connections 连接草稿；连接 ID 保持稳定以便默认引用跟随。
 */
data class ModelServiceDraft(
    val instanceId: String?,
    val templateId: String?,
    val name: String,
    val isOfficialToolsEnabled: Boolean,
    val sharedApiKey: String,
    val connections: List<ServiceConnection>,
) {
    /** 草稿是否要求厂商工具开关；自定义服务与未集成工具的模板不需要。 */
    val supportsOfficialTools: Boolean
        get() = templateId != null && OFFICIAL_TOOL_TEMPLATE_IDS.contains(templateId)

    fun connection(connectionId: String): ServiceConnection? =
        connections.firstOrNull { it.id == connectionId }

    fun connection(capability: ServiceCapability): ServiceConnection? =
        connections.firstOrNull { it.capability == capability }

    /** 对话连接；凭据合并与建议流程都以它为准。 */
    val conversationConnection: ServiceConnection?
        get() = connection(ServiceCapability.Conversation)

    /** 连接的生效凭据：连接级覆盖优先，回落到草稿共享凭据。 */
    fun effectiveApiKey(connection: ServiceConnection): String = when (val source = connection.credential) {
        CredentialSource.Shared -> sharedApiKey
        is CredentialSource.Independent -> source.apiKey
    }

    fun withConnection(connection: ServiceConnection): ModelServiceDraft = copy(
        connections = connections.map { if (it.id == connection.id) connection else it },
    )

    companion object {
        /** 已集成厂商工具的模板；其它模板不展示厂商工具入口。 */
        val OFFICIAL_TOOL_TEMPLATE_IDS: Set<String> = setOf(
            "deepseek", "anthropic", "minimax", "gemini", "glm", "kimi",
        )
    }
}

/**
 * 内置厂商模板：描述应用已集成的接入能力与默认连接配置。
 *
 * 模板是静态元数据，不含用户凭据；实例由模板派生并独立保存。
 * 模板 ID 同时是厂商适配（语音协议、厂商工具矩阵、品牌图标）的分派键。
 *
 * @property id 模板稳定 ID（厂商唯一）。
 * @property name 品牌展示名。
 * @property iconRes 品牌图标 drawable 资源 ID；null 时回退默认机器人图标。
 * @property homepageUrl 平台官方主页。
 * @property keyHelpUrl 获取密钥的帮助页地址。
 * @property connections 模板集成的能力连接定义。
 */
data class ModelServiceTemplate(
    val id: String,
    val name: String,
    val iconRes: Int? = null,
    val homepageUrl: String = "",
    val keyHelpUrl: String = "",
    val connections: List<TemplateConnection>,
) {
    /** 按能力类别取模板连接；模板未集成该能力时返回 null。 */
    fun connection(capability: ServiceCapability): TemplateConnection? =
        connections.firstOrNull { it.capability == capability }

    /** 是否集成了对话能力；决定自定义模型管理入口的形态。 */
    val supportsConversation: Boolean
        get() = connections.any { it.capability == ServiceCapability.Conversation }
}

/**
 * 模板连接定义：能力类别、支持协议、端点策略、默认端点与候选模型目录。
 *
 * 双协议厂商（OpenAI 兼容 + Anthropic 兼容）在 [defaultEndpoints] 中按协议给出默认
 * 地址；用户切换协议时端点回落到新协议的模板默认值。
 *
 * @property capability 能力类别。
 * @property protocols 该连接支持的协议集合；UI 协议下拉按此过滤。
 * @property defaultEndpoints 按协议的默认端点；VendorManaged 连接的固定官方端点。
 * @property endpointPolicy 端点策略；[EndpointPolicy.VendorManaged] 时用户不可编辑。
 * @property endpointSchemes 允许的地址协议集合（如 `http`/`https`/`wss`）。
 * @property supportsWorkspace 是否支持可选业务空间 ID（百炼）。
 * @property catalog 内置候选模型目录；对话连接的目录可由远端刷新补充。
 */
data class TemplateConnection(
    val capability: ServiceCapability,
    val protocols: List<ApiProtocol>,
    val defaultEndpoints: Map<ApiProtocol, String>,
    val endpointPolicy: EndpointPolicy,
    val endpointSchemes: Set<String>,
    val supportsWorkspace: Boolean = false,
    val catalog: List<Model> = emptyList(),
) {
    /** 模板是否支持 [protocol]；持久化的非法取值据此回落。 */
    fun supports(protocol: ApiProtocol): Boolean = protocol in protocols

    /** 协议对应的模板默认端点；映射缺失时回退到任一默认值。 */
    fun defaultEndpoint(protocol: ApiProtocol): String =
        defaultEndpoints[protocol] ?: defaultEndpoints.values.firstOrNull().orEmpty()
}

/**
 * 运行时解析后的连接视图：凭据与端点的唯一运行时来源。
 *
 * 语音协议适配、连接探测与会话模型构造都消费该视图，不直接读取实例或草稿，
 * 保证共享凭据/独立覆盖在唯一一处收敛。
 *
 * @property instanceId 归属实例 ID。
 * @property templateId 厂商模板 ID；null 表示自定义服务（仅对话连接）。
 * @property connectionId 连接稳定 ID。
 * @property capability 连接能力类别。
 * @property protocol 接入协议。
 * @property endpoint 生效端点（已 trim）。
 * @property apiKey 生效凭据（已取首个、trim）。
 * @property workspaceId 业务空间 ID；仅百炼消费。
 * @property model 解析到的模型（含能力与多模态元数据）。
 */
data class ResolvedServiceConnection(
    val instanceId: String,
    val templateId: String?,
    val connectionId: String,
    val capability: ServiceCapability,
    val protocol: ApiProtocol,
    val endpoint: String,
    val apiKey: String,
    val workspaceId: String?,
    val model: Model,
) {
    /** 模型 ID；请求体携带的精确标识。 */
    val modelId: String get() = model.id

    /** 模型声明的识别能力（[ModelCapability.STT] 或 [ModelCapability.ASR]）；非识别模型为 null。 */
    val recognitionCapability: ModelCapability?
        get() = model.recognitionCapability
}

/**
 * 删除影响：引用目标实例的默认角色与音色绑定。
 *
 * @property roles 引用该实例模型（或整体服务）的默认角色。
 * @property hasTtsVoiceBinding 朗读音色默认值是否绑定在该实例的合成模型上。
 * @property hasRealtimeVoiceBinding 通话音色覆盖是否绑定在该实例的实时模型上。
 */
data class DeletionImpact(
    val roles: List<DefaultModelRole>,
    val hasTtsVoiceBinding: Boolean,
    val hasRealtimeVoiceBinding: Boolean,
) {
    /** 是否存在任何需要用户确认的影响。 */
    val hasImpact: Boolean
        get() = roles.isNotEmpty() || hasTtsVoiceBinding || hasRealtimeVoiceBinding
}

/**
 * 默认模型角色；删除与默认建议流程按角色报告影响。
 *
 * 音色不属于独立角色：朗读音色跟随合成默认，通话音色跟随实时默认。
 */
enum class DefaultModelRole { Assistant, Fast, SpeechRecognition, SpeechSynthesis, Realtime }

/**
 * 按选择在实例快照中解析连接视图：实例、连接、模型、生效凭据与端点。
 *
 * 服务停用、凭据为空或模型不存在时返回 null；模型能力校验由调用方按用途执行。
 * 运行时仓库实现与界面快照解析共用这一份语义，避免判定分叉。
 */
fun List<ModelServiceInstance>.resolveConnection(selection: ModelSelection?): ResolvedServiceConnection? {
    if (selection == null) return null
    val instance = firstOrNull { it.id == selection.instanceId } ?: return null
    if (!instance.isEnabled) return null
    val connection = instance.connection(selection.connectionId) ?: return null
    // 多凭据以逗号分隔时取首个，与语音请求构造的密钥惯例一致。
    val apiKey = instance.effectiveApiKey(connection).substringBefore(',').trim()
    if (apiKey.isBlank()) return null
    val model = instance.findModel(selection) ?: return null
    return ResolvedServiceConnection(
        instanceId = instance.id,
        templateId = instance.templateId,
        connectionId = connection.id,
        capability = connection.capability,
        protocol = connection.protocol,
        endpoint = connection.endpoint.trim(),
        apiKey = apiKey,
        workspaceId = connection.workspaceId?.trim()?.ifEmpty { null },
        model = model,
    )
}
