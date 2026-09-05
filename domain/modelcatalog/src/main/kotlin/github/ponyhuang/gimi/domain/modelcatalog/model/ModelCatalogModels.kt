package github.ponyhuang.gimi.domain.modelcatalog.model

import kotlinx.serialization.Serializable

data class LLMModelSetting(
    val id: String,
    val name: String,
    val isEnabled: Boolean,
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
) {
    val activeApiBaseUrl: String
        get() = when (apiProtocol) {
            ApiProtocol.Standard -> apiBaseUrl
            ApiProtocol.Anthropic -> anthropicBaseUrl
            // Gemini 走 ADK 原生实现，无需请求地址；保留 apiBaseUrl 仅供连接测试 / 模型刷新。
            ApiProtocol.Gemini -> apiBaseUrl
        }

    val openAiCompatibleBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')
}

data class ModelGroup(
    val id: String,
    val name: String,
    val models: List<Model>,
)

data class Model(
    val id: String,
    val name: String,
    val isStt: Boolean = false,
    val isTts: Boolean = false,
    val capabilities: MultimodalCapabilities = MultimodalCapabilities(),
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
)

enum class ApiProtocol {
    Standard,
    Anthropic,
    Gemini,
}

/** 双协议厂商（OpenAI 兼容 + Anthropic 兼容）允许的接口协议集合；Gemini 仅自家厂商使用。 */
val DUAL_API_PROTOCOLS: List<ApiProtocol> = listOf(ApiProtocol.Standard, ApiProtocol.Anthropic)

sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data object Ready : CatalogLoadState
    data class Failed(val cause: Throwable) : CatalogLoadState
}
