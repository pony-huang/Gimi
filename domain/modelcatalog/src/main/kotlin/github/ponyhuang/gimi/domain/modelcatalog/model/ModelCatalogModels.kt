package github.ponyhuang.gimi.domain.modelcatalog.model

import kotlinx.serialization.Serializable

/**
 * 服务种类。
 *
 * - [Chat]：聊天服务，走 HTTP 文本接口（OpenAI 兼容 / Anthropic / Gemini）。
 * - [Speech]：独立语音服务，走 HTTP 或 WebSocket，不参与聊天路由，使用内置或手动模型目录。
 *   原生实时语音（单模型承担听/想/说的常驻全双工会话）不再是独立服务种类：
 *   它以 [ModelCapability.Realtime] 模型标记挂在同厂商的 [Speech] 服务下，与级联语音共用配置。
 */
enum class ModelServiceKind { Chat, Speech }

/**
 * 连接测试结果。
 *
 * 区分密钥问题、网络问题与服务本身不支持该测试，避免把"密钥无效"提示成"网络异常"
 * 导致用户反复重试，也避免用错的协议去连接。
 */
enum class ServiceConnectionResult { Succeeded, AuthFailed, NetworkFailed, Unsupported }

/** 厂商连接配置与用户选择的模型；未添加项只作为厂商目录模板。 */
data class LLMModelSetting(
    val id: String,
    val name: String,
    val isEnabled: Boolean,
    /** 是否把该服务可独立调用的官方工具暴露给会话工具抽屉。 */
    val isOfficialToolsEnabled: Boolean = isEnabled,
    val apiKey: String,
    val apiBaseUrl: String,
    val apiProtocol: ApiProtocol,
    /** 厂商允许的接口协议集合；单协议厂商（OpenAI / Anthropic / Gemini）在 UI 上锁定协议切换。 */
    val supportedProtocols: List<ApiProtocol> = DUAL_API_PROTOCOLS,
    val anthropicBaseUrl: String,
    val groups: List<ModelGroup>,
    val iconRes: Int? = null,
    val homepageUrl: String = "",
    val keyHelpUrl: String = "",
    val isAdded: Boolean = apiKey.isNotBlank(),
    /**
     * 服务种类。
     *
     * 语音服务的 [apiBaseUrl] 保存厂商语音协议的接入端点（如 `wss://`），
     * HTTP 语音服务固定使用其接入协议，WebSocket 服务不使用聊天协议切换。
     * 端点由厂商自有的服务该值仅作兜底展示，不参与请求构造。
     */
    val kind: ModelServiceKind = ModelServiceKind.Chat,
    /** 厂商模板允许的地址协议，不随用户编辑地址改变。 */
    val endpointSchemes: Set<String> = if (kind == ModelServiceKind.Chat) setOf("http", "https") else setOf("wss"),
    /**
     * 阿里云百炼业务空间 ID（可选）。
     *
     * 留空走官方默认域名（Key 所属的默认业务空间）；填写后语音请求改写为业务空间专属
     * 域名并携带 `X-DashScope-WorkSpace` 头。目前仅千问语音服务消费该字段，其它厂商忽略。
     */
    val workspaceId: String? = null,
) {
    val activeApiBaseUrl: String
        get() = when (apiProtocol) {
            ApiProtocol.OpenaiChatcompletions -> apiBaseUrl
            ApiProtocol.Anthropic -> anthropicBaseUrl
            // Gemini 走 ADK 原生实现，无需请求地址；保留 apiBaseUrl 仅供连接测试 / 模型刷新。
            ApiProtocol.Gemini -> apiBaseUrl
        }

    /**
     * 聊天协议基址（HTTP）。
     *
     * 适用于 HTTP 服务；WebSocket 语音服务不使用此属性构造请求。
     */
    val openAiCompatibleBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    /**
     * 用户配置的接入地址（去掉首尾空白与末尾斜杠）。
     *
     * 语音能力据此构造请求地址：端点可配置的服务直接使用该值，
     * 端点由厂商自有的服务忽略该值。字段名不预设地址形态——聊天服务保存 HTTP 基址，
     * 语音服务可能保存 `wss://` 端点。
     */
    val configuredBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')}

data class ModelGroup(
    val id: String,
    val name: String,
    val models: List<Model>,
)

/**
 * 模型目录中的一个模型。
 *
 * @property capabilities 该模型可承担的任务集合；默认视为可对话的 [ModelCapability.LLM] 模型，
 *   专用语音模型需显式声明 [ModelCapability.STT] / [ModelCapability.TTS] / [ModelCapability.ASR]。
 * @property multimodal 该模型可接收的输入模态，与 [capabilities] 正交。
 */
data class Model(
    val id: String,
    val name: String,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.LLM),
    val multimodal: MultimodalCapabilities = MultimodalCapabilities(),
)

@Serializable
data class ModelSelection(
    // 默认空串容忍旧版本遗漏字段的持久化数据（kotlinx 对缺失必需字段抛异常，Gson 静默留 null）。
    val serviceId: String = "",
    val groupId: String = "",
    val modelId: String = "",
)

data class DefaultModelSettings(
    val services: List<LLMModelSetting>,
    val assistantSelection: ModelSelection?,
    val fastSelection: ModelSelection?,
    val speechSelection: ModelSelection?,
    val ttsSelection: ModelSelection?,
    val ttsVoiceId: String,
    /** Realtime 模式的默认模型；null 表示未配置，Realtime 模式因此不可进入。 */
    val realtimeSelection: ModelSelection? = null,
    /** Realtime 会话音色的自定义覆盖；null 表示用所选模型在该厂商下的缺省音色。 */
    val realtimeVoice: RealtimeVoiceOverride? = null,
)

/**
 * Realtime 会话音色的自定义覆盖。
 *
 * 音色与模型强绑定：同一厂商不同代际的 Realtime 模型音色集互不相同，跨模型使用会被服务端拒绝。
 * 因此覆盖值记下它所绑定的模型；当前选择的模型与之不同时该覆盖失效，回落到模型缺省音色。
 *
 * @property selection 覆盖值绑定的 Realtime 模型。
 * @property voiceId 用户填写的音色 ID（厂商内置音色或声音复刻 voice_id）。
 */
@Serializable
data class RealtimeVoiceOverride(
    val selection: ModelSelection,
    val voiceId: String,
)

enum class ApiProtocol {
    /** OpenAI Chat Completions 及兼容协议，与未来的 Responses 协议分别建模。 */
    OpenaiChatcompletions,
    Anthropic,
    Gemini,
}

/** 双协议厂商（OpenAI 兼容 + Anthropic 兼容）允许的接口协议集合；Gemini 仅自家厂商使用。 */
val DUAL_API_PROTOCOLS: List<ApiProtocol> = listOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic)

sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data object Ready : CatalogLoadState
    data class Failed(val cause: Throwable) : CatalogLoadState
}
