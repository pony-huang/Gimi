package github.ponyhuang.gimi.domain.modelcatalog.model

data class LLMModelSetting(
    val id: String,
    val name: String,
    val isEnabled: Boolean,
    val apiKey: String,
    val apiBaseUrl: String,
    val apiProtocol: ApiProtocol,
    /** 厂商允许的接口协议集合；单协议厂商（OpenAI / Anthropic）在 UI 上锁定协议切换。 */
    val supportedProtocols: List<ApiProtocol> = ApiProtocol.entries,
    val anthropicBaseUrl: String,
    val groups: List<ModelGroup>,
    val iconRes: Int? = null,
    val homepageUrl: String = "",
    val keyHelpUrl: String = "",
    /** Official tools exposed by this provider. They are enabled by default. */
    val supportedOfficialTools: List<String> = emptyList(),
) {
    val activeApiBaseUrl: String
        get() = when (apiProtocol) {
            ApiProtocol.Standard -> apiBaseUrl
            ApiProtocol.Anthropic -> anthropicBaseUrl
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

data class ModelSelection(
    val serviceId: String,
    val groupId: String,
    val modelId: String,
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
}

sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data object Ready : CatalogLoadState
    data class Failed(val cause: Throwable) : CatalogLoadState
}
