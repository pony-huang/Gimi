package github.ponyhuang.asssistantai.data

import github.ponyhuang.asssistantai.data.DefaultModelServices.iconFor
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds

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
 * @property LLMModelGroups 该服务下的模型组列表。
 * @property iconRes 品牌图标 drawable 资源 ID；null 时回退到默认机器人图标。
 *                  新增厂商时只需在 [DefaultModelServices] 中赋值即可，无需改 UI 调用点。
 * @property homepageUrl 平台官方主页（Header 外链目标）。
 * @property keyHelpUrl "点击这里获取密钥" 富文本跳转目标。
 * @property docsUrl "深度求索 文档" 富文本跳转目标。
 * @property modelsUrl "模型" 富文本跳转目标。
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
    val LLMModelGroups: List<LLMModelGroup> = emptyList(),
    val iconRes: Int? = null,
    val homepageUrl: String = "",
    val keyHelpUrl: String = "",
    val docsUrl: String = "",
    val modelsUrl: String = "",
    val officialToolProtocols: Map<String, List<ApiBaseType>> = emptyMap(),
    val disabledOfficialTools: Set<String> = emptySet(),
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

    /** Official tools are directly authorized by default; settings persist only opt-outs. */
    val enabledOfficialTools: List<String>
        get() = supportedOfficialTools.filterNot(disabledOfficialTools::contains)
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
)

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

/**
 * 内置 / 默认模型服务清单。新增厂商时在这里追加一条 [LLMModelProvider]，
 * 同时给 [LLMModelProvider.iconRes] 赋值，品牌图标就会在所有 UI 调用点自动生效。
 */
object DefaultModelServices {

    val services: List<LLMModelProvider> = listOf(
        LLMModelProvider(
            serviceId = "deepseek",
            serviceName = "Deepseek",
            isEnabled = false,
            apiKey = "",
            apiBaseUrl = "https://api.deepseek.com",
            baseType = ApiBaseType.Anthropic,
            anthropicBaseUrl = "https://api.deepseek.com/anthropic",
            LLMModelGroups = listOf(),
            homepageUrl = "https://www.deepseek.com/",
            keyHelpUrl = "https://platform.deepseek.com/api_keys",
            docsUrl = "https://api-docs.deepseek.com/",
            modelsUrl = "https://api-docs.deepseek.com/quick_start/pricing",
        ),
        LLMModelProvider(
            serviceId = "minimax",
            serviceName = "MiniMax",
            isEnabled = false,
            apiKey = "",
            apiBaseUrl = "https://api.minimaxi.com/v1",
            baseType = ApiBaseType.Standard,
            anthropicBaseUrl = "https://api.minimaxi.com/anthropic",
            LLMModelGroups = listOf(),
            homepageUrl = "https://www.minimaxi.com/",
            keyHelpUrl = "https://platform.minimaxi.com/user-center/basic-information/interface-key",
            docsUrl = "https://platform.minimaxi.com/document",
            modelsUrl = "https://platform.minimaxi.com/document/Models",
            officialToolProtocols = mapOf(
                OfficialToolIds.WEB_SEARCH to listOf(ApiBaseType.Anthropic),
            ),
        ),
        LLMModelProvider(
            serviceId = "mimo",
            serviceName = "Xiaomi MIMO",
            isEnabled = false,
            apiKey = "",
            apiBaseUrl = "https://api.xiaomimimo.com/v1",
            baseType = ApiBaseType.Standard,
            anthropicBaseUrl = "https://api.xiaomimimo.com/anthropic",
            LLMModelGroups = listOf(),
            homepageUrl = "https://mimo.mi.com/",
            keyHelpUrl = "https://platform.xiaomimimo.com/console/api-keys",
            docsUrl = "https://mimo.mi.com/docs",
            modelsUrl = "https://mimo.mi.com/docs/zh-CN/quick-start/summary/model",
            officialToolProtocols = mapOf(
                OfficialToolIds.WEB_SEARCH to listOf(ApiBaseType.Standard),
            ),
        ),
        LLMModelProvider(
            serviceId = "openai",
            serviceName = "OpenAI",
            isEnabled = false,
            apiKey = "",
            apiBaseUrl = "https://api.openai.com/v1",
            baseType = ApiBaseType.Standard,
            supportedBaseTypes = listOf(ApiBaseType.Standard),
            LLMModelGroups = listOf(),
            homepageUrl = "https://openai.com/",
            keyHelpUrl = "https://platform.openai.com/api-keys",
            docsUrl = "https://platform.openai.com/docs",
            modelsUrl = "https://platform.openai.com/docs/models",
        ),
        LLMModelProvider(
            serviceId = "anthropic",
            serviceName = "Anthropic",
            isEnabled = false,
            apiKey = "",
            apiBaseUrl = "https://api.anthropic.com",
            baseType = ApiBaseType.Anthropic,
            supportedBaseTypes = listOf(ApiBaseType.Anthropic),
            anthropicBaseUrl = "https://api.anthropic.com",
            LLMModelGroups = listOf(),
            homepageUrl = "https://www.anthropic.com/",
            keyHelpUrl = "https://console.anthropic.com/settings/keys",
            docsUrl = "https://docs.anthropic.com/",
            modelsUrl = "https://docs.anthropic.com/en/docs/about-claude/models",
        ),
        LLMModelProvider(
            serviceId = "kimi",
            serviceName = "Moonshot",
            isEnabled = false,
            apiKey = "",
            apiBaseUrl = "https://api.moonshot.cn/v1",
            baseType = ApiBaseType.Standard,
            anthropicBaseUrl = "https://api.moonshot.cn/anthropic",
            LLMModelGroups = listOf(),
            homepageUrl = "https://www.kimi.com/",
            keyHelpUrl = "https://platform.kimi.com/console/api-keys",
            docsUrl = "https://platform.kimi.com/docs",
            modelsUrl = "https://platform.kimi.com/docs/api/models-overview",
            officialToolProtocols = mapOf(
                OfficialToolIds.KIMI_FORMULAS to ApiBaseType.entries,
            ),
        ),
    ).sortedBy { it.serviceId }

    /**
     * 按 [serviceId] 查找品牌图标。供 UI 层在仅有 serviceId 时使用
     * 给列表里没声明 [LLMModelProvider.iconRes] 的服务提供兜底。
     */
    fun iconFor(serviceId: String): Int? =
        services.firstOrNull { it.serviceId == serviceId }?.iconRes

    /**
     * 按 [serviceId] 查找厂商允许的接口标准集合（静态元数据，与 [iconFor] 一样
     * 不随用户设置变化）。未声明的自定义服务回退为两种协议皆可。
     */
    fun supportedBaseTypesFor(serviceId: String): List<ApiBaseType> =
        services.firstOrNull { it.serviceId == serviceId }?.supportedBaseTypes
            ?: ApiBaseType.entries

    fun officialToolProtocolsFor(serviceId: String): Map<String, List<ApiBaseType>> =
        services.firstOrNull { it.serviceId == serviceId }?.officialToolProtocols.orEmpty()
}
