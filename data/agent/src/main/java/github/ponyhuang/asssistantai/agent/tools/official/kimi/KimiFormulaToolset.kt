package github.ponyhuang.asssistantai.agent.tools.official.kimi

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import javax.inject.Inject
import okhttp3.OkHttpClient

/**
 * Exposes Moonshot formulas as ADK tools — the agent-side [OfficialToolset]
 * paradigm that the protocol-native toolsets are modeled after.
 *
 * Owns the remote manifest fetch, per-call [BaseTool] instantiation, and
 * user-level function filtering (from
 * [ModelConfig.enabledOfficialFunctions]).
 */
class KimiFormulaToolset @Inject constructor(
    private val httpClient: OkHttpClient,
) : OfficialToolset {
    override suspend fun resolveTools(config: ModelConfig): List<BaseTool> {
        if (OfficialToolIds.KIMI_FORMULAS !in config.officialTools) return emptyList()
        val enabledFunctionIds = config.enabledOfficialFunctions[OfficialToolIds.KIMI_FORMULAS]
            ?.takeIf {
                it.isNotEmpty() && ConversationToolConfiguration.ALL_FUNCTIONS_MARKER !in it
            }
        return KimiFormulaManifest(apiKey = config.apiKey, httpClient = httpClient)
            .fetch()
            .map { declaration ->
                KimiFormulaTool(
                    apiKey = config.apiKey,
                    declaration = declaration,
                    httpClient = httpClient,
                )
            }
            .filter { tool -> enabledFunctionIds == null || tool.name in enabledFunctionIds }
    }
}
