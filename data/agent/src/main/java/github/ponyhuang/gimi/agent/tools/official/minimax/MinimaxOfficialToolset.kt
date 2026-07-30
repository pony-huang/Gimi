package github.ponyhuang.gimi.agent.tools.official.minimax

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.agent.tools.official.OfficialBuiltInTool
import github.ponyhuang.gimi.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import javax.inject.Inject

/**
 * MiniMax 官方工具声明。
 *
 * MiniMax 的 Web Search 仅由 Anthropic 兼容端点支持；同时按服务 ID 隔离，
 * 避免用户自建的 Anthropic 协议服务被误注入 MiniMax 专属工具。
 */
class MinimaxOfficialToolset @Inject constructor() : OfficialToolset {
    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (config.serviceId != "minimax" || config.baseType != ApiProtocol.Anthropic) {
            return emptyList()
        }
        return listOf(WEB_SEARCH_TOOL_ID)
            .filter { toolId -> selection.isOfficialToolEnabled(config.serviceId, toolId) }
            .map(::OfficialBuiltInTool)
    }

    internal companion object {
        const val WEB_SEARCH_TOOL_ID: String = "web_search"
    }
}
