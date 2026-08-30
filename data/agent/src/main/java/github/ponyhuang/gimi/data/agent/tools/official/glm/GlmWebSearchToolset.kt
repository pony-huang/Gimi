package github.ponyhuang.gimi.data.agent.tools.official.glm

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.apiKeyForService
import github.ponyhuang.gimi.data.agent.tools.official.belongsToModelFamily
import github.ponyhuang.gimi.data.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Exposes the GLM Web Search and Reader APIs as executable ADK tools — the agent-side
 * [OfficialToolset] paradigm shared with the Kimi formula toolset.
 *
 * GLM serves both capabilities through standalone endpoints rather than protocol-native
 * built-in declarations, so they are resolved (and executed) locally for both the Standard
 * and Anthropic protocols. Each function is gated by the conversation-level selection.
 */
class GlmWebSearchToolset @Inject constructor(
    private val httpClient: OkHttpClient,
    private val modelServices: AgentModelConfigurationSource,
) : OfficialToolset {
    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (!config.modelId.belongsToModelFamily("glm")) return emptyList()
        val apiKey = modelServices.apiKeyForService(config.serviceId) ?: return emptyList()
        if (
            !selection.isOfficialToolEnabled(
                config.serviceId,
                TOOL_ID,
            )
        ) {
            return emptyList()
        }

        val selectedFunctionIds = selection?.enabledOfficialFunctionIds(
            config.serviceId,
            TOOL_ID,
        )
        val api = GlmWebToolApi(
            apiKey = apiKey,
            baseUrl = config.fullBaseUrl,
            httpClient = httpClient,
        )
        return listOf(
            GlmWebSearchTool(api),
            GlmReaderTool(api),
        ).filter { selectedFunctionIds == null || it.name in selectedFunctionIds }
    }

    internal companion object {
        const val TOOL_ID: String = "glm_web_search"
    }
}
