package github.ponyhuang.gimi.data.agent.tools.official.gemini

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.GoogleMapsTool
import com.google.adk.kt.tools.GoogleSearchTool
import com.google.adk.kt.tools.UrlContextTool
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import javax.inject.Inject

/**
 * Official toolset for Gemini.
 *
 * 直接复用 ADK 自带的厂商内置工具（[GoogleSearchTool] / [UrlContextTool] / [GoogleMapsTool]）：
 * 它们没有函数声明，`processLlmRequest` 会把原生 `Tool(googleSearch/urlContext/googleMaps)`
 * 注入请求 config，由 ADK 原生 Gemini 模型透传给 genai SDK，无需包装层转换。
 */
class GeminiOfficialToolset @Inject constructor() : OfficialToolset {
    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (config.serviceId != SERVICE_ID || config.baseType != ApiProtocol.Gemini) {
            return emptyList()
        }
        return buildList {
            if (selection.isOfficialToolEnabled(config.serviceId, WEB_SEARCH_TOOL_ID)) {
                add(GoogleSearchTool())
            }
            if (selection.isOfficialToolEnabled(config.serviceId, URL_CONTEXT_TOOL_ID)) {
                add(UrlContextTool())
            }
            if (selection.isOfficialToolEnabled(config.serviceId, GOOGLE_MAPS_TOOL_ID)) {
                add(GoogleMapsTool())
            }
        }
    }

    companion object {
        const val SERVICE_ID: String = "gemini"
        const val WEB_SEARCH_TOOL_ID: String = "web_search"
        const val URL_CONTEXT_TOOL_ID: String = "url_context"
        const val GOOGLE_MAPS_TOOL_ID: String = "google_maps"
    }
}
