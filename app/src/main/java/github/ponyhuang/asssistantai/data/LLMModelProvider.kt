package github.ponyhuang.asssistantai.data

import androidx.annotation.DrawableRes
import github.ponyhuang.asssistantai.BuildConfig
import github.ponyhuang.asssistantai.R

/**
 * 模型服务与配置中心 — 数据契约。
 * @property serviceId 平台唯一 ID（如 `"deepseek"`）。
 * @property serviceName 中文 / 品牌展示名（如 `"深度求索"`）。
 * @property isEnabled 总开关；false 时列表页不显示 ON 胶囊，且 Agent 不应路由到此服务。
 * @property apiKey API 密钥；可填多个，逗号分隔（UI HelperText 已说明）。
 * @property baseType 接口标准类型，决定预览拼接路径。
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
    val anthropicBaseUrl: String = apiBaseUrl,
    val LLMModelGroups: List<LLMModelGroup> = emptyList(),
    @DrawableRes val iconRes: Int? = null,
    val homepageUrl: String = "",
    val keyHelpUrl: String = "",
    val docsUrl: String = "",
    val modelsUrl: String = "",
) {
    /** 当前 [baseType] 对应的实际请求地址。两种协议的地址分别保存，切换时互不覆盖。 */
    val activeApiBaseUrl: String
        get() = when (baseType) {
            ApiBaseType.Standard -> apiBaseUrl
            ApiBaseType.Anthropic -> anthropicBaseUrl
        }
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
            serviceName = "深度求索",
            isEnabled = true,
            apiKey = BuildConfig.DEEPSEEK_API_KEY,
            apiBaseUrl = "https://api.deepseek.com",
            baseType = ApiBaseType.Anthropic,
            anthropicBaseUrl = "https://api.deepseek.com/anthropic",
            LLMModelGroups = listOf(
                LLMModelGroup(
                    groupId = "deepseek-chat",
                    groupName = "DeepSeek Chat",
                    isExpanded = true,
                    models = listOf(
                        LLMModelItem(
                            modelId = "deepseek-v4-pro",
                            modelName = "deepseek-v4-pro",
                        ),
                        LLMModelItem(
                            modelId = "deepseek-v4-flash",
                            modelName = "deepseek-v4-flash",
                        ),
                    ),
                ),
            ),
            iconRes = R.drawable.ic_model_provider_deepseek,
            homepageUrl = "https://www.deepseek.com/",
            keyHelpUrl = "https://platform.deepseek.com/api_keys",
            docsUrl = "https://api-docs.deepseek.com/",
            modelsUrl = "https://api-docs.deepseek.com/quick_start/pricing",
        ),
        LLMModelProvider(
            serviceId = "minimax",
            serviceName = "MiniMax",
            isEnabled = true,
            apiKey = BuildConfig.MINIMAX_API_KEY,
            apiBaseUrl = "https://api.minimaxi.com/v1",
            baseType = ApiBaseType.Standard,
            anthropicBaseUrl = "https://api.minimaxi.com/anthropic",
            LLMModelGroups = listOf(
                LLMModelGroup(
                    groupId = "minimax-chat",
                    groupName = "MiniMax Chat",
                    isExpanded = true,
                    models = listOf(
                        LLMModelItem(
                            modelId = "MiniMax-M3",
                            modelName = "MiniMax-M3",
                        ),
                    ),
                ),
            ),
            iconRes = R.drawable.ic_model_provider_minimax,
            homepageUrl = "https://www.minimaxi.com/",
            keyHelpUrl = "https://platform.minimaxi.com/user-center/basic-information/interface-key",
            docsUrl = "https://platform.minimaxi.com/document",
            modelsUrl = "https://platform.minimaxi.com/document/Models",
        ),
        LLMModelProvider(
            serviceId = "mimo",
            serviceName = "Xiaomi MIMO",
            isEnabled = false,
            apiKey = "",
            apiBaseUrl = "https://api.xiaomimimo.com/v1",
            baseType = ApiBaseType.Standard,
            anthropicBaseUrl = "https://api.xiaomimimo.com/anthropic",
            LLMModelGroups = listOf(
                LLMModelGroup(
                    groupId = "MiMo-V2.5",
                    groupName = "MiMo-V2.5",
                    isExpanded = true,
                    models = listOf(
                        LLMModelItem(
                            modelId = "mimo-v2.5-pro-ultraspeed",
                            modelName = "mimo-v2.5-pro-ultraspeed",
                        ),
                        LLMModelItem(
                            modelId = "mimo-v2.5-pro",
                            modelName = "mimo-v2.5-pro",
                        ),
                        LLMModelItem(
                            modelId = "mimo-v2.5",
                            modelName = "mimo-v2.5",
                        ),
                    ),
                ),
                LLMModelGroup(
                    groupId = "MiMo-V2.5-ASR",
                    groupName = "MiMo-V2.5 ASR",
                    isExpanded = true,
                    models = listOf(
                        LLMModelItem(
                            modelId = "mimo-v2.5-asr",
                            modelName = "mimo-v2.5-asr",
                            isStt = true,
                        ),
                    ),
                ),
                LLMModelGroup(
                    groupId = "MiMo-V2.5-TTS",
                    groupName = "MiMo-V2.5 TTS",
                    isExpanded = true,
                    models = listOf(
                        LLMModelItem(
                            modelId = "mimo-v2.5-tts",
                            modelName = "mimo-v2.5-tts",
                            isTts = true,
                        ),
                    ),
                ),
            ),
            iconRes = R.drawable.ic_model_provider_xiaomimimo,
            homepageUrl = "https://mimo.mi.com/",
            keyHelpUrl = "https://platform.xiaomimimo.com/console/api-keys",
            docsUrl = "https://mimo.mi.com/docs",
            modelsUrl = "https://mimo.mi.com/docs/zh-CN/quick-start/summary/model",
        ),
    )

    /**
     * 按 [serviceId] 查找品牌图标。供 UI 层在仅有 serviceId 时使用
     * （例如聊天 TopAppBar 中 [ModelServiceIcon]）。
     * 给列表里没声明 [LLMModelProvider.iconRes] 的服务提供兜底。
     */
    fun iconFor(serviceId: String): Int? =
        services.firstOrNull { it.serviceId == serviceId }?.iconRes
}
