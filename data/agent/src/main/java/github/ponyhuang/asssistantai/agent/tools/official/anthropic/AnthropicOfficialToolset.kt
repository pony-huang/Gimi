package github.ponyhuang.asssistantai.agent.tools.official.anthropic

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.ModelRuntimeMetadata
import github.ponyhuang.asssistantai.agent.tools.official.OfficialBuiltInTool
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import javax.inject.Inject

/**
 * Official toolset for the Anthropic protocol (Anthropic, MiniMax).
 *
 * Adds declaration-only tools for the Anthropic protocol. [github.ponyhuang.asssistantai.agent.model.Claude]
 * converts these reserved declarations to their provider-native wire shapes.
 */
class AnthropicOfficialToolset @Inject constructor() : OfficialToolset {
    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (config.baseType != ApiProtocol.Anthropic) return emptyList()
        return listOf(OfficialToolIds.WEB_SEARCH)
            .filter { toolId -> selection.isOfficialToolEnabled(config.serviceId, toolId) }
            .map(::OfficialBuiltInTool)
    }
}
