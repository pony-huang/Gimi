package github.ponyhuang.asssistantai.agent.tools.official.openai

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
 * Official toolset for the OpenAI-Compatible protocol (OpenAI, Mimo).
 *
 * Adds declaration-only tools for the OpenAI-compatible protocol. [github.ponyhuang.asssistantai.agent.model.Openai]
 * converts these reserved declarations to their provider-native wire shapes.
 */
class OpenaiOfficialToolset @Inject constructor() : OfficialToolset {
    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (config.baseType != ApiProtocol.Standard) return emptyList()
        return listOf(OfficialToolIds.WEB_SEARCH)
            .filter { toolId -> selection.isOfficialToolEnabled(config.serviceId, toolId) }
            .map(::OfficialBuiltInTool)
    }
}
