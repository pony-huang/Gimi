package github.ponyhuang.asssistantai.data.modelcatalog

import github.ponyhuang.asssistantai.domain.modelcatalog.model.MultimodalCapabilities

private const val WEB_SEARCH_TOOL_ID: String = "web_search"
private const val KIMI_FORMULAS_TOOL_ID: String = "kimi_formulas"
private const val GLM_WEB_SEARCH_TOOL_ID: String = "glm_web_search"

/**
 * 模型服务与配置中心 — 数据契约。
 * @property serviceId 平台唯一 ID（如 `"deepseek"`）。
 * @property serviceName 中文 / 品牌展示名（如 `"深度求索"`）。
 * @property isEnabled 总开关；false 时列表页不显示 ON 胶囊，且 Agent 不应路由到此服务。
 * @property apiKey API 密钥；可填多个，逗号分隔（UI HelperText 已说明）。
 * @property baseType 接口标准类型，决定预览拼接路径。
 * @property supportedBaseTypes 该厂商允许的接口标准集合。OpenAI 仅支持 Standard，
 *                  Anthropic 仅支持 Anthropic，其余厂商两种皆可。UI 协议下拉按此过滤，
 *                  持久化的非法取值会在加载时回退到集合首项。
 * @property lLMModelGroups 该服务下的模型组列表。
 * @property iconRes 品牌图标 drawable 资源 ID；null 时回退到默认机器人图标。
 *                  新增厂商时只需在 [LLMModelConfigs] 中赋值即可，无需改 UI 调用点。
 * @property homepageUrl 平台官方主页（Header 外链目标）。
 * @property keyHelpUrl "点击这里获取密钥" 富文本跳转目标。
 * @property officialToolProtocols 官方工具 ID 到支持协议的静态映射；运行时按当前协议
 * 计算菜单可见能力，不保存到用户配置。
 */
data class LLMModelProvider(
    val serviceId: String,
    val serviceName: String,
    val isEnabled: Boolean,
    val apiKey: String,
    val apiBaseUrl: String,
    val baseType: ApiBaseType = ApiBaseType.Standard,
    val supportedBaseTypes: List<ApiBaseType> = ApiBaseType.entries,
    val anthropicBaseUrl: String = apiBaseUrl,
    val lLMModelGroups: List<LLMModelGroup> = emptyList(),
    val iconRes: Int? = null,
    val homepageUrl: String = "",
    val keyHelpUrl: String = "",
    val officialToolProtocols: Map<String, List<ApiBaseType>> = emptyMap(),
) {
    /** 当前 [baseType] 对应的实际请求地址。两种协议的地址分别保存，切换时互不覆盖。 */
    val activeApiBaseUrl: String
        get() = when (baseType) {
            ApiBaseType.Standard -> apiBaseUrl
            ApiBaseType.Anthropic -> anthropicBaseUrl
        }

    val supportedOfficialTools: List<String>
        get() = officialToolProtocols
            .filterValues { baseType in it }
            .keys
            .toList()

}

/**
 * 一个模型组 / 系列（例如 "DeepSeek Chat"）。
 *
 * @property groupId 组唯一 ID。
 * @property groupName 组名（UI 行展示）。
 * @property isExpanded UI 默认展开状态；详情页用本地 `Set<String>` 覆盖此默认值。
 * @property models 组内具体模型列表。
 */
data class LLMModelGroup(
    val groupId: String,
    val groupName: String,
    val isExpanded: Boolean = true,
    val models: List<LLMModelItem> = emptyList(),
)

/**
 * 组内的具体模型实例。
 *
 * @property modelId 平台精确模型 ID（如 `deepseek-v4-pro`），用于请求体。
 * @property modelName 模型展示名（如 `DeepSeek V4 Pro`）。
 * @property isStt 是否为专用语音识别模型；此类模型不能用于普通聊天。
 * @property isTts 是否为专用语音合成模型；此类模型不能用于普通聊天。
 * @property isChildPanelExpanded 占位字段 — 后续可挂"子配置面板"。
 */
data class LLMModelItem(
    val modelId: String,
    val modelName: String,
    val isStt: Boolean = false,
    val isTts: Boolean = false,
    val isChildPanelExpanded: Boolean = false,
    val capabilities: MultimodalCapabilities = MultimodalCapabilities(),
)

val LLMModelItem.supportsImages: Boolean get() = capabilities.supportsImages
val LLMModelItem.supportsAudio: Boolean get() = capabilities.supportsAudio
val LLMModelItem.supportsDocuments: Boolean get() = capabilities.supportsDocuments

/** Whether this model can be used for ordinary assistant chat. */
val LLMModelItem.isChatModel: Boolean
    get() = !isStt && !isTts

/** Whether this provider is fully configured and allowed to serve chat requests. */
val LLMModelProvider.isConfiguredForChat: Boolean
    get() = isEnabled && apiKey.isNotBlank()

/**
 * API 地址接口标准。
 */
enum class ApiBaseType() {
    Standard,
    Anthropic,
}

enum class LLMModelType(
    val serviceId: String,
    val serviceName: String,
    val defaultBaseUrl: String,
    val defaultBaseType: ApiBaseType,
    val supportedBaseTypes: List<ApiBaseType>,
    val defaultAnthropicBaseUrl: String? = null,
    val homepageUrl: String = "",
    val keyHelpUrl: String = "",
    val officialToolProtocols: Map<String, List<ApiBaseType>> = emptyMap(),
    val iconRes: Int? = null,
) {
    DeepSeek(
        serviceId = "deepseek",
        serviceName = "Deepseek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultBaseType = ApiBaseType.Anthropic,
        supportedBaseTypes = ApiBaseType.entries,
        defaultAnthropicBaseUrl = "https://api.deepseek.com/anthropic",
        homepageUrl = "https://www.deepseek.com/",
        keyHelpUrl = "https://platform.deepseek.com/api_keys",
    ),
    MiniMax(
        serviceId = "minimax",
        serviceName = "MiniMax",
        defaultBaseUrl = "https://api.minimaxi.com/v1",
        defaultBaseType = ApiBaseType.Standard,
        supportedBaseTypes = ApiBaseType.entries,
        defaultAnthropicBaseUrl = "https://api.minimaxi.com/anthropic",
        homepageUrl = "https://www.minimaxi.com/",
        keyHelpUrl = "https://platform.minimaxi.com/user-center/basic-information/interface-key",
        officialToolProtocols = mapOf(
            WEB_SEARCH_TOOL_ID to listOf(ApiBaseType.Anthropic),
        ),
    ),
    Mimo(
        serviceId = "mimo",
        serviceName = "MIMO",
        defaultBaseUrl = "https://api.xiaomimimo.com/v1",
        defaultBaseType = ApiBaseType.Standard,
        supportedBaseTypes = ApiBaseType.entries,
        defaultAnthropicBaseUrl = "https://api.xiaomimimo.com/anthropic",
        homepageUrl = "https://mimo.mi.com/",
        keyHelpUrl = "https://platform.xiaomimimo.com/console/api-keys",
        officialToolProtocols = mapOf(
            WEB_SEARCH_TOOL_ID to listOf(ApiBaseType.Standard),
        ),
    ),
    OpenAI(
        serviceId = "openai",
        serviceName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultBaseType = ApiBaseType.Standard,
        supportedBaseTypes = listOf(ApiBaseType.Standard),
        homepageUrl = "https://openai.com/",
        keyHelpUrl = "https://platform.openai.com/api-keys",
        officialToolProtocols = mapOf(
            WEB_SEARCH_TOOL_ID to listOf(ApiBaseType.Standard),
        ),
    ),
    Anthropic(
        serviceId = "anthropic",
        serviceName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultBaseType = ApiBaseType.Anthropic,
        supportedBaseTypes = listOf(ApiBaseType.Anthropic),
        defaultAnthropicBaseUrl = "https://api.anthropic.com",
        homepageUrl = "https://www.anthropic.com/",
        keyHelpUrl = "https://console.anthropic.com/settings/keys",
        officialToolProtocols = mapOf(
            WEB_SEARCH_TOOL_ID to listOf(ApiBaseType.Anthropic),
        ),
    ),
    Moonshot(
        serviceId = "kimi",
        serviceName = "Moonshot",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        defaultBaseType = ApiBaseType.Standard,
        supportedBaseTypes = ApiBaseType.entries,
        defaultAnthropicBaseUrl = "https://api.moonshot.cn/anthropic",
        homepageUrl = "https://www.kimi.com/",
        keyHelpUrl = "https://platform.kimi.com/console/api-keys",
        officialToolProtocols = mapOf(
            KIMI_FORMULAS_TOOL_ID to ApiBaseType.entries,
        ),
    ),
    Glm(
        serviceId = "glm",
        serviceName = "GLM",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        defaultBaseType = ApiBaseType.Anthropic,
        supportedBaseTypes = ApiBaseType.entries,
        defaultAnthropicBaseUrl = "https://open.bigmodel.cn/api/anthropic",
        homepageUrl = "https://bigmodel.cn/",
        keyHelpUrl = "https://bigmodel.cn/usercenter/proj-mgmt/apikeys",
        officialToolProtocols = mapOf(
            GLM_WEB_SEARCH_TOOL_ID to ApiBaseType.entries,
        ),
    );

    fun toProvider(
        modelGroups: List<LLMModelGroup> = emptyList(),
        isEnabled: Boolean = false,
        apiKey: String = "",
    ): LLMModelProvider = LLMModelProvider(
        serviceId = serviceId,
        serviceName = serviceName,
        isEnabled = isEnabled,
        apiKey = apiKey,
        apiBaseUrl = defaultBaseUrl,
        baseType = defaultBaseType,
        supportedBaseTypes = supportedBaseTypes,
        anthropicBaseUrl = defaultAnthropicBaseUrl ?: defaultBaseUrl,
        lLMModelGroups = modelGroups,
        homepageUrl = homepageUrl,
        keyHelpUrl = keyHelpUrl,
        officialToolProtocols = officialToolProtocols,
        iconRes = iconRes,
    )

    companion object {
        fun fromServiceId(serviceId: String): LLMModelType? =
            entries.firstOrNull { it.serviceId == serviceId }
    }
}

/**
 * 内置 / 默认模型服务清单。新增厂商时在这里追加一条 [LLMModelProvider]，
 * 同时给 [LLMModelProvider.iconRes] 赋值，品牌图标就会在所有 UI 调用点自动生效。
 */
object LLMModelConfigs {

    val services: List<LLMModelProvider> = listOf(
        LLMModelType.DeepSeek.toProvider(),
        LLMModelType.MiniMax.toProvider(
            modelGroups = listOf(
                LLMModelGroup(
                    groupId = "minimax-tts",
                    groupName = "Minimax Speech",
                    isExpanded = true,
                    models = listOf(
                        LLMModelItem("speech-2.8-hd", "speech-2.8-hd", isTts = true),
                        LLMModelItem("speech-2.8-turbo", "speech-2.8-turbo", isTts = true),
                        LLMModelItem("speech-2.6-hd", "speech-2.6-hd", isTts = true),
                        LLMModelItem("speech-2.6-turbo", "speech-2.6-turbo", isTts = true),
                        LLMModelItem("speech-02-hd", "speech-02-hd", isTts = true),
                        LLMModelItem("speech-02-turbo", "speech-02-turbo", isTts = true),
                    ),
                ),
            ),
        ),
        LLMModelType.Mimo.toProvider(),
        LLMModelType.OpenAI.toProvider(),
        LLMModelType.Anthropic.toProvider(),
        LLMModelType.Moonshot.toProvider(),
        LLMModelType.Glm.toProvider(),
    ).sortedBy { it.serviceId }

    fun fromServiceId(serviceId: String): LLMModelType? =
        LLMModelType.fromServiceId(serviceId)

    fun iconFor(serviceId: String): Int? =
        fromServiceId(serviceId)?.iconRes

    fun supportedBaseTypesFor(serviceId: String): List<ApiBaseType> =
        fromServiceId(serviceId)?.supportedBaseTypes ?: ApiBaseType.entries

    fun officialToolProtocolsFor(serviceId: String): Map<String, List<ApiBaseType>> =
        fromServiceId(serviceId)?.officialToolProtocols.orEmpty()
}
