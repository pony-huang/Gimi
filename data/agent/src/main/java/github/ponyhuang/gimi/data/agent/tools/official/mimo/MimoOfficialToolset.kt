package github.ponyhuang.gimi.data.agent.tools.official.mimo

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.official.OfficialBuiltInTool
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import javax.inject.Inject

/**
 * MiMo 官方工具声明。
 *
 * MiMo 的 Web Search 仅由 OpenAI 兼容端点支持；同时按服务 ID 隔离，
 * 避免其他 Standard 协议服务被误注入 MiMo 专属工具。
 */
class MimoOfficialToolset @Inject constructor() : OfficialToolset {
    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (config.serviceId != "mimo" || config.baseType != ApiProtocol.Standard) {
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
