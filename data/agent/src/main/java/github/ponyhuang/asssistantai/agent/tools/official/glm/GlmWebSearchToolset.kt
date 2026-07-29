package github.ponyhuang.asssistantai.agent.tools.official.glm

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import javax.inject.Inject
import okhttp3.OkHttpClient

/**
 * Exposes the GLM Web Search API as an executable ADK tool — the agent-side
 * [OfficialToolset] paradigm shared with the Kimi formula toolset.
 *
 * GLM serves search through a standalone endpoint rather than a protocol-native
 * built-in declaration, so the tool is resolved (and executed) locally for both
 * the Standard and Anthropic protocols. Function-level filtering is unnecessary:
 * the tool exposes a single static function already gated by
 * `ModelConfig.forConversation`.
 */
class GlmWebSearchToolset @Inject constructor(
    private val httpClient: OkHttpClient,
) : OfficialToolset {
    override suspend fun resolveTools(config: ModelConfig): List<BaseTool> {
        if (OfficialToolIds.GLM_WEB_SEARCH !in config.officialTools) return emptyList()
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
