package github.ponyhuang.asssistantai.agent.tools.official

import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.WebSearchTool20250305
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.WebSearchTool
import github.ponyhuang.asssistantai.agent.tools.official.kimi.KimiFormulaToolset
import github.ponyhuang.asssistantai.data.ApiBaseType
import github.ponyhuang.asssistantai.data.LLMModelType
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One user-selectable official tool contribution.
 *
 * Native tools contribute an ADK placeholder which is translated by a protocol adapter.
 * Agent tools contribute a [Toolset] and keep their provider-specific transport isolated.
 */
interface OfficialToolProvider {
    val id: String

    fun contribute(config: ModelConfig): OfficialToolContribution
}

data class OfficialToolContribution(
    val tools: List<BaseTool> = emptyList(),
    val toolsets: List<Toolset> = emptyList(),
) {
    operator fun plus(other: OfficialToolContribution): OfficialToolContribution =
        OfficialToolContribution(
            tools = tools + other.tools,
            toolsets = toolsets + other.toolsets,
        )

    fun deduplicated(): OfficialToolContribution = OfficialToolContribution(
        tools = tools.distinctBy(BaseTool::name),
        toolsets = toolsets,
    )
}

@Singleton
class OfficialToolRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards OfficialToolProvider>,
) {
    fun resolve(config: ModelConfig): OfficialToolContribution {
        val providersById = providers.groupBy(OfficialToolProvider::id)
        return config.officialTools
            .fold(OfficialToolContribution()) { result, id ->
                providersById[id].orEmpty().fold(result) { contribution, provider ->
                    contribution + provider.contribute(config)
                }
            }
            .deduplicated()
    }
}

class WebSearchOfficialToolProvider @Inject constructor() : OfficialToolProvider {
    override val id: String = OfficialToolIds.WEB_SEARCH

    override fun contribute(config: ModelConfig): OfficialToolContribution =
        OfficialToolContribution(tools = listOf(WebSearchTool()))
}

class KimiFormulaOfficialToolProvider @Inject constructor(
    private val httpClient: OkHttpClient,
) : OfficialToolProvider {
    override val id: String = OfficialToolIds.KIMI_FORMULAS

    override fun contribute(config: ModelConfig): OfficialToolContribution {
        if (config.serviceId != LLMModelType.Moonshot.serviceId) return OfficialToolContribution()
        val enabledFunctionIds = config.enabledOfficialFunctions[OfficialToolIds.KIMI_FORMULAS]
            ?.takeIf { it.isNotEmpty() && ConversationToolConfiguration.ALL_FUNCTIONS_MARKER !in it }
        return OfficialToolContribution(
            toolsets = listOf(
                KimiFormulaToolset(
                    apiKey = config.apiKey,
                    baseUrl = config.officialToolBaseUrl,
                    httpClient = httpClient,
                    enabledFunctionIds = enabledFunctionIds,
                ),
            ),
        )
    }
}

interface IOpenAiOfficialToolAdapter {
    fun adapt(config: ModelConfig, tools: List<ChatCompletionTool>): List<ChatCompletionTool>

    fun supports(config: ModelConfig): Boolean = false
}

open class OpenAiOfficialToolAdapter @Inject constructor() : IOpenAiOfficialToolAdapter {
    override fun adapt(
        config: ModelConfig,
        tools: List<ChatCompletionTool>,
    ): List<ChatCompletionTool> {
        if (!supports(config)) {
            return tools
        }
        val index = tools.indexOfFirst { tool ->
            tool.isFunction() &&
                    tool.asFunction().function().name() == OfficialToolIds.WEB_SEARCH
        }
        if (index < 0) return tools

        val nativeWebSearch = ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder()
                .type(JsonValue.from(OfficialToolIds.WEB_SEARCH))
                .function(
                    FunctionDefinition.builder()
                        .name(OfficialToolIds.WEB_SEARCH)
                        .putAdditionalProperty(
                            "type",
                            JsonValue.from(OfficialToolIds.WEB_SEARCH),
                        )
                        .build(),
                )
                .build(),
        )
        return tools.toMutableList().apply { this[index] = nativeWebSearch }
    }

    override fun supports(config: ModelConfig): Boolean {
        return config.serviceId in listOf(LLMModelType.OpenAI.serviceId, LLMModelType.Mimo.serviceId) &&
                config.baseType == ApiBaseType.Standard &&
                OfficialToolIds.WEB_SEARCH in config.officialTools
    }
}

class MimoWebSearchToolAdapter @Inject constructor() : OpenAiOfficialToolAdapter() {
    override fun supports(config: ModelConfig): Boolean {
        return !(config.serviceId != LLMModelType.Mimo.serviceId ||
                config.baseType != ApiBaseType.Standard ||
                OfficialToolIds.WEB_SEARCH !in config.officialTools)
    }
}

interface IAnthropicOfficialToolAdapter {
    fun adapt(config: ModelConfig, tools: List<ToolUnion>): List<ToolUnion>

    fun supports(config: ModelConfig): Boolean = false
}

open class AnthropicOfficialToolAdapter @Inject constructor() : IAnthropicOfficialToolAdapter {
    override fun adapt(
        config: ModelConfig,
        tools: List<ToolUnion>,
    ): List<ToolUnion> {
        if (!supports(config)) {
            return tools
        }
        val index = tools.indexOfFirst { tool ->
            tool.isTool() && tool.asTool().name() == OfficialToolIds.WEB_SEARCH
        }
        if (index < 0) return tools

        val nativeWebSearch = ToolUnion.ofWebSearchTool20250305(
            WebSearchTool20250305.builder().build(),
        )
        return tools.toMutableList().apply { this[index] = nativeWebSearch }
    }

    override fun supports(config: ModelConfig): Boolean {
        return config.serviceId in listOf(LLMModelType.Anthropic.serviceId, LLMModelType.MiniMax.serviceId) &&
                config.baseType == ApiBaseType.Anthropic &&
                OfficialToolIds.WEB_SEARCH in config.officialTools
    }
}

