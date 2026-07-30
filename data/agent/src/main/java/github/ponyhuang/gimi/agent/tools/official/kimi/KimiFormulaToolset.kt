package github.ponyhuang.gimi.agent.tools.official.kimi

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.agent.tools.official.SearchOfficialToolset
import github.ponyhuang.gimi.agent.tools.official.apiKeyForService
import github.ponyhuang.gimi.agent.tools.official.belongsToModelFamily
import github.ponyhuang.gimi.agent.tools.official.isOfficialToolEnabled
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import okhttp3.OkHttpClient

/**
 * Exposes Moonshot formulas as ADK tools — the agent-side [OfficialToolset]
 * paradigm that the protocol-native toolsets are modeled after.
 *
 * Owns the remote manifest fetch, per-call [BaseTool] instantiation, and
 * user-level function filtering (from the conversation tool configuration
 * carried by the invocation).
 */
class KimiFormulaToolset @Inject constructor(
    private val cache: KimiFormulaCache,
    private val httpClient: OkHttpClient,
    private val modelServices: AgentModelConfigurationSource,
) : SearchOfficialToolset {
    override val sourceId: String = "official:$TOOL_ID"
    override val sourceDisplayName: String = "Kimi formulas"

    override suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool> {
        if (!config.modelId.belongsToModelFamily("kimi", "moonshot")) return emptyList()
        val apiKey = modelServices.apiKeyForService(config.serviceId) ?: return emptyList()
        if (!selection.isOfficialToolEnabled(config.serviceId, TOOL_ID)) {
            return emptyList()
        }
        val enabledFunctionIds = selection
            ?.enabledOfficialFunctionIds(config.serviceId, TOOL_ID)
            ?.takeIf {
                it.isNotEmpty() && ConversationToolConfiguration.ALL_FUNCTIONS_MARKER !in it
            }
        return cache.fetch(
            serviceId = config.serviceId,
            apiKey = apiKey,
        )
            .map { declaration ->
                KimiFormulaTool(
                    apiKey = apiKey,
                    declaration = declaration,
                    httpClient = httpClient,
                )
            }
            .filter { tool -> enabledFunctionIds == null || tool.name in enabledFunctionIds }
    }

    internal companion object {
        const val TOOL_ID: String = "kimi_formulas"
    }
}
