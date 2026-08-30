package github.ponyhuang.gimi.data.agent.tools.official.openai

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.official.OfficialBuiltInTool
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import javax.inject.Inject

/**
 * Official toolset for OpenAI.
 *
 * Adds declaration-only tools for OpenAI's API. [github.ponyhuang.gimi.data.agent.model.Openai]
 * converts these reserved declarations to their provider-native wire shapes.
 */
class OpenaiOfficialToolset @Inject constructor() : OfficialToolset {
    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (config.serviceId != "openai" || config.baseType != ApiProtocol.Standard) {
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
