package github.ponyhuang.asssistantai.agent.tools.official.glm

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import javax.inject.Inject
import okhttp3.OkHttpClient

/**
 * Exposes the GLM Web Search API as an executable ADK tool — the agent-side
 * [OfficialToolset] paradigm shared with the Kimi formula toolset.
 *
 * GLM serves search through a standalone endpoint rather than a protocol-native
 * built-in declaration, so the tool is resolved (and executed) locally for both
 * the Standard and Anthropic protocols. The tool exposes a single static function,
 * gated by the conversation-level official tool selection.
 */
class GlmWebSearchToolset @Inject constructor(
    private val httpClient: OkHttpClient,
) : OfficialToolset {
    override suspend fun resolveTools(
        config: ModelConfig,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (OfficialToolIds.GLM_WEB_SEARCH !in config.officialTools) return emptyList()
        if (!selection.isOfficialToolEnabled(config.serviceId, OfficialToolIds.GLM_WEB_SEARCH)) {
            return emptyList()
        }
        return listOf(
            GlmWebSearchTool(
                api = GlmWebSearchApi(
                    apiKey = config.apiKey,
                    baseUrl = config.fullBaseUrl,
                    httpClient = httpClient,
                ),
            ),
        )
    }
}
