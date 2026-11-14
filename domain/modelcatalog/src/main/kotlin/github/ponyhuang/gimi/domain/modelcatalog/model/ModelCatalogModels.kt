package github.ponyhuang.gimi.domain.modelcatalog.model

import kotlinx.serialization.Serializable

/**
 * 连接测试结果。
 *
 * 区分密钥问题、网络问题与服务本身不支持该测试，避免把"密钥无效"提示成"网络异常"
 * 导致用户反复重试，也避免用错的协议去连接。
 */
enum class ServiceConnectionResult { Succeeded, AuthFailed, NetworkFailed, Unsupported }

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

/**
 * 模型选择：引用实例、连接与模型 ID。
 *
 * @property instanceId 模型服务实例 ID；不同实例的同名模型互不混淆。
 * @property connectionId 连接稳定 ID；能力类别与凭据按连接解析。
 * @property modelId 实际请求的模型 ID。
 */
@Serializable
data class ModelSelection(
    // 默认空串容忍旧版本遗漏字段的持久化数据（kotlinx 对缺失必需字段抛异常，Gson 静默留 null）。
    val instanceId: String = "",
    val connectionId: String = "",
    val modelId: String = "",
)

data class DefaultModelSettings(
    val instances: List<ModelServiceInstance>,
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
