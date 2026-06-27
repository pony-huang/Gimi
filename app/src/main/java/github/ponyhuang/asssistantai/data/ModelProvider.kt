package github.ponyhuang.asssistantai.data

import github.ponyhuang.asssistantai.BuildConfig

/**
 * 模型服务与配置中心 — 数据契约。
 *
 * 单一服务下挂若干模型组（[ModelGroup]），每个模型组下挂若干具体模型（[ModelItem]）。
 * 本文件的所有字段对齐 OpenSpec `model-service-domain-model` spec。
 */

/**
 * 一个模型服务平台（深度求索、MiniMax 等）。
 *
 * @property serviceId 平台唯一 ID（如 `"deepseek"`）。
 * @property serviceName 中文 / 品牌展示名（如 `"深度求索"`）。
 * @property isEnabled 总开关；false 时列表页不显示 ON 胶囊，且 Agent 不应路由到此服务。
 * @property apiKey API 密钥；可填多个，逗号分隔（UI HelperText 已说明）。
 * @property baseType 接口标准类型，决定预览拼接路径。
 * @property modelGroups 该服务下的模型组列表。
 * @property homepageUrl 平台官方主页（Header 外链目标）。
 * @property keyHelpUrl "点击这里获取密钥" 富文本跳转目标。
 * @property docsUrl "深度求索 文档" 富文本跳转目标。
 * @property modelsUrl "模型" 富文本跳转目标。
 */
data class ModelProvider(
    val serviceId: String,
    val serviceName: String,
    val isEnabled: Boolean,
    val apiKey: String,
    val apiBaseUrl: String,
    val baseType: ApiBaseType = ApiBaseType.Standard,
    val anthropicBaseUrl: String = apiBaseUrl,
    val modelGroups: List<ModelGroup> = emptyList(),
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
data class ModelGroup(
    val groupId: String,
    val groupName: String,
    val isExpanded: Boolean = true,
    val models: List<ModelItem> = emptyList(),
)

/**
 * 组内的具体模型实例。
 *
 * @property modelId 平台精确模型 ID（如 `deepseek-v4-pro`），用于请求体。
 * @property modelName 模型展示名（如 `DeepSeek V4 Pro`）。
 * @property isChildPanelExpanded 占位字段 — 后续可挂"子配置面板"。
 */
data class ModelItem(
    val modelId: String,
    val modelName: String,
    val isChildPanelExpanded: Boolean = false,
)

/**
 * API 地址接口标准。
 *
 * - [Standard]：OpenAI 兼容，chat completions 路径 `/v1/chat/completions`。
 * - [Anthropic]：Anthropic 兼容，messages 路径 `/v1/messages`。
 *
 * UI 层根据 [suffix] 渲染下拉选项、根据 [previewPath] 拼预览 URL。
 */
enum class ApiBaseType(val suffix: String, val previewPath: String) {
    Standard(suffix = "standard", previewPath = "/v1/chat/completions"),
    Anthropic(suffix = "anthropic", previewPath = "/v1/messages"),
}

object DefaultModelServices {

    val services: List<ModelProvider> = listOf(
        ModelProvider(
            serviceId = "deepseek",
            serviceName = "深度求索",
            isEnabled = true,
            apiKey = BuildConfig.DEEPSEEK_API_KEY,
            apiBaseUrl = "https://api.deepseek.com",
            baseType = ApiBaseType.Anthropic,
            anthropicBaseUrl = "https://api.deepseek.com/anthropic",
            modelGroups = listOf(
                ModelGroup(
                    groupId = "deepseek-chat",
                    groupName = "DeepSeek Chat",
                    isExpanded = true,
                    models = listOf(
                        ModelItem(
                            modelId = "deepseek-v4-pro",
                            modelName = "DeepSeek V4 Pro",
                        ),
                        ModelItem(
                            modelId = "deepseek-v4-flash",
                            modelName = "DeepSeek V4 Flash",
                        ),
                    ),
                ),
            ),
            homepageUrl = "https://www.deepseek.com/",
            keyHelpUrl = "https://platform.deepseek.com/api_keys",
            docsUrl = "https://api-docs.deepseek.com/",
            modelsUrl = "https://api-docs.deepseek.com/quick_start/pricing",
        ),
        ModelProvider(
            serviceId = "minimax",
            serviceName = "MiniMax",
            isEnabled = true,
            apiKey = BuildConfig.MINIMAX_API_KEY,
            apiBaseUrl = "https://api.minimaxi.com/v1",
            baseType = ApiBaseType.Standard,
            anthropicBaseUrl = "https://api.minimaxi.com/anthropic",
            modelGroups = listOf(
                ModelGroup(
                    groupId = "minimax-chat",
                    groupName = "MiniMax Chat",
                    isExpanded = true,
                    models = listOf(
                        ModelItem(
                            modelId = "MiniMax-M3",
                            modelName = "MiniMax M3",
                        ),
                    ),
                ),
            ),
            homepageUrl = "https://www.minimaxi.com/",
            keyHelpUrl = "https://platform.minimaxi.com/user-center/basic-information/interface-key",
            docsUrl = "https://platform.minimaxi.com/document",
            modelsUrl = "https://platform.minimaxi.com/document/Models",
        )
    )
}
