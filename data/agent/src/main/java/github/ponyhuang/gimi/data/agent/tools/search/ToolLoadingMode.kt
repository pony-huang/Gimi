package github.ponyhuang.gimi.data.agent.tools.search

import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import java.net.URI

/** 工具搜索的协议加载方式；未适配端点始终返回普通搜索结果。 */
internal enum class ToolLoadingMode { RESULT, DEEPSEEK_REFERENCE }

internal fun toolLoadingMode(runtime: ModelRuntimeMetadata?): ToolLoadingMode {
    if (runtime?.baseType != ApiProtocol.Anthropic || runtime.serviceId != "deepseek") {
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

internal const val DEFERRED_CATALOG = "selkie.deferred_tool_catalog"
