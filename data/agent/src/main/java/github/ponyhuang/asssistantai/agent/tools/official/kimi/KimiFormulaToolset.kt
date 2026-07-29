package github.ponyhuang.asssistantai.agent.tools.official.kimi

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.NativeToolSpec
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
    override val protocolId: String = "kimi"

    override fun isApplicable(config: ModelConfig): Boolean =
        config.serviceId == MOONSHOT_SERVICE_ID &&
                OfficialToolIds.KIMI_FORMULAS in config.officialTools

    override suspend fun getTools(config: ModelConfig): List<BaseTool> {
        if (!isApplicable(config)) return emptyList()
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

    override fun openAiNativeSpecs(config: ModelConfig): List<NativeToolSpec> = emptyList()

    override fun anthropicNativeSpecs(config: ModelConfig): List<NativeToolSpec> = emptyList()

    companion object {
        // 与 :data:modelcatalog 的 LLMModelType.Moonshot.serviceId 保持一致；
        // data 层模块不允许互相依赖，此处复制字面量。
        const val MOONSHOT_SERVICE_ID: String = "kimi"
    }
}
