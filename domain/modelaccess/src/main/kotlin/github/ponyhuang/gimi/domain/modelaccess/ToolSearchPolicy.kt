package github.ponyhuang.gimi.domain.modelaccess

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import java.net.URI

/** 工具搜索的协议加载方式；未适配端点始终返回普通搜索结果。 */
enum class ToolLoadingMode { RESULT, DEEPSEEK_REFERENCE }

/**
 * 官方 DeepSeek /anthropic 端点判定；全部 DeepSeek Messages 协议特例
 * （工具引用加载、会话标题、latest_reminder）共用此门控。
 *
 * 第三方代理可能不识别厂商扩展字段，因此仅官方端点启用特例，代理回落通用协议。
 */
fun isOfficialDeepSeekAnthropicEndpoint(runtime: ModelRuntimeMetadata?): Boolean {
    if (runtime?.baseType != ApiProtocol.Anthropic || runtime.templateId != "deepseek") return false
    val endpoint = try { URI(runtime.fullBaseUrl) } catch (_: java.net.URISyntaxException) { return false }
    return endpoint.scheme == "https" && endpoint.host == "api.deepseek.com" &&
        endpoint.port in listOf(-1, 443) && endpoint.userInfo == null && endpoint.query == null &&
        endpoint.fragment == null && endpoint.path.trimEnd('/') in listOf("/anthropic", "/anthropic/v1")
}

fun toolLoadingMode(runtime: ModelRuntimeMetadata?): ToolLoadingMode {
    if (!isOfficialDeepSeekAnthropicEndpoint(runtime)) return ToolLoadingMode.RESULT
    // 工具引用（tool_reference/defer_loading）目前仅 deepseek-flash 声明支持。
    return if (runtime?.modelId == "deepseek-flash") {
        ToolLoadingMode.DEEPSEEK_REFERENCE
    } else ToolLoadingMode.RESULT
}

/** 已适配模型延迟加载的工具目录写入 Event.config 的键；协议转换器据此摘除声明。 */
const val DEFERRED_CATALOG = "gimi.deferred_tool_catalog"

/** 内联工具定义写入 Event.partMetadata 的键；转换器据此加载声明，工具集据此写入。 */
const val INLINE_DEFINITIONS = "gimi.inline_tool_definitions"

/** 内联工具搜索工具的协议名；转换器与工具集共用。 */
const val TOOL_SEARCH_NAME: String = "tool_search"

/** DeepSeek 会话标题随首轮回答顺带返回时的透传键；写入 LlmResponse.customMetadata。 */
const val CONVERSATION_TITLE_METADATA_KEY = "gimi.conversation_title"
