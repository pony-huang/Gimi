package github.ponyhuang.gimi.data.modelaccess.anthropic.llm

import github.ponyhuang.gimi.data.modelaccess.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import java.net.URI

/** 工具搜索的协议加载方式；未适配端点始终返回普通搜索结果。 */
enum class ToolLoadingMode { RESULT, DEEPSEEK_REFERENCE }

fun toolLoadingMode(runtime: ModelRuntimeMetadata?): ToolLoadingMode {
    if (runtime?.baseType != ApiProtocol.Anthropic || runtime.templateId != "deepseek") {
        return ToolLoadingMode.RESULT
    }
    val endpoint = try { URI(runtime.fullBaseUrl) } catch (_: java.net.URISyntaxException) { return ToolLoadingMode.RESULT }
    val official = endpoint.scheme == "https" && endpoint.host == "api.deepseek.com" &&
        endpoint.port in listOf(-1, 443) && endpoint.userInfo == null && endpoint.query == null &&
        endpoint.fragment == null && endpoint.path.trimEnd('/') in listOf("/anthropic", "/anthropic/v1")
    return if (official && runtime.modelId == "deepseek-flash") {
        ToolLoadingMode.DEEPSEEK_REFERENCE
    } else ToolLoadingMode.RESULT
}

const val DEFERRED_CATALOG = "gimi.deferred_tool_catalog"

/** 内联工具定义写入 Event.partMetadata 的键；转换器据此加载声明，工具集据此写入。 */
const val INLINE_DEFINITIONS = "gimi.inline_tool_definitions"

/** 内联工具搜索工具的协议名；转换器与工具集共用。 */
const val TOOL_SEARCH_NAME: String = "tool_search"
