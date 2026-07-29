package github.ponyhuang.asssistantai.agent.tools.official

import com.anthropic.models.messages.ToolUnion
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import com.openai.models.chat.completions.ChatCompletionTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

// 与 :data:modelcatalog 的 LLMModelType.serviceId 保持一致；data 层模块不允许
// 互相依赖，此处复制字面量。
private const val OPENAI_SERVICE_ID = "openai"
private const val MIMO_SERVICE_ID = "mimo"
private const val ANTHROPIC_SERVICE_ID = "anthropic"
private const val MINIMAX_SERVICE_ID = "minimax"

/**
 * Everything the official toolsets contribute for one [ModelConfig]: local
 * [BaseTool]s, protocol-level ADK [Toolset]s (reserved; currently unused),
 * and vendor-native specs merged into the request by the protocol adapters.
 */
data class OfficialToolContribution(
    val tools: List<BaseTool> = emptyList(),
    val toolsets: List<Toolset> = emptyList(),
    val openAiNativeSpecs: List<NativeToolSpec.OpenAi> = emptyList(),
    val anthropicNativeSpecs: List<NativeToolSpec.Anthropic> = emptyList(),
) {
    operator fun plus(other: OfficialToolContribution): OfficialToolContribution =
        OfficialToolContribution(
            tools = tools + other.tools,
            toolsets = toolsets + other.toolsets,
            openAiNativeSpecs = openAiNativeSpecs + other.openAiNativeSpecs,
            anthropicNativeSpecs = anthropicNativeSpecs + other.anthropicNativeSpecs,
        )

    fun deduplicated(): OfficialToolContribution = copy(
        tools = tools.distinctBy(BaseTool::name),
        openAiNativeSpecs = openAiNativeSpecs.distinctBy { it.toolId },
        anthropicNativeSpecs = anthropicNativeSpecs.distinctBy { it.toolId },
    )
}

/**
 * Sums the contributions of every applicable [OfficialToolset] for a config.
 */
@Singleton
class OfficialToolRegistry @Inject constructor(
    private val toolsets: Set<@JvmSuppressWildcards OfficialToolset>,
) {
    suspend fun resolve(config: ModelConfig): OfficialToolContribution =
        coroutineScope {
            val applicable = toolsets.filter { it.isApplicable(config) }
            val baseTools = applicable
                .map { async { it.getTools(config) } }
                .awaitAll()
                .flatten()
            val openAi = applicable.flatMap { it.openAiNativeSpecs(config) }
                .filterIsInstance<NativeToolSpec.OpenAi>()
            val anthropic = applicable.flatMap { it.anthropicNativeSpecs(config) }
                .filterIsInstance<NativeToolSpec.Anthropic>()
            OfficialToolContribution(
                tools = baseTools,
                openAiNativeSpecs = openAi,
                anthropicNativeSpecs = anthropic,
            ).deduplicated()
        }
}

/**
 * Merges vendor-native [NativeToolSpec.OpenAi] specs into the outgoing
 * request tools, deduplicating by [NativeToolSpec.toolId].
 */
interface IOpenAiOfficialToolAdapter {
    fun adapt(
        config: ModelConfig,
        tools: List<ChatCompletionTool>,
        specs: List<NativeToolSpec.OpenAi>,
    ): List<ChatCompletionTool>

    fun supports(config: ModelConfig): Boolean = false
}

class OpenAiOfficialToolAdapter @Inject constructor() : IOpenAiOfficialToolAdapter {
    override fun supports(config: ModelConfig): Boolean =
        config.baseType == ApiProtocol.Standard &&
                config.serviceId in listOf(OPENAI_SERVICE_ID, MIMO_SERVICE_ID)

    override fun adapt(
        config: ModelConfig,
        tools: List<ChatCompletionTool>,
        specs: List<NativeToolSpec.OpenAi>,
    ): List<ChatCompletionTool> {
        if (!supports(config) || specs.isEmpty()) return tools
        val existing = tools.filter { it.isFunction() }
            .mapTo(mutableSetOf()) { it.asFunction().function().name() }
        val toAppend = specs.filterNot { it.toolId in existing }.map { it.tool }
        return if (toAppend.isEmpty()) tools else tools + toAppend
    }
}

/**
 * Anthropic-protocol dual of [IOpenAiOfficialToolAdapter].
 */
interface IAnthropicOfficialToolAdapter {
    fun adapt(
        config: ModelConfig,
        tools: List<ToolUnion>,
        specs: List<NativeToolSpec.Anthropic>,
    ): List<ToolUnion>

    fun supports(config: ModelConfig): Boolean = false
}

class AnthropicOfficialToolAdapter @Inject constructor() : IAnthropicOfficialToolAdapter {
    override fun supports(config: ModelConfig): Boolean =
        config.baseType == ApiProtocol.Anthropic &&
                config.serviceId in listOf(ANTHROPIC_SERVICE_ID, MINIMAX_SERVICE_ID)

    override fun adapt(
        config: ModelConfig,
        tools: List<ToolUnion>,
        specs: List<NativeToolSpec.Anthropic>,
    ): List<ToolUnion> {
        if (!supports(config) || specs.isEmpty()) return tools
        val existing = buildSet {
            tools.forEach { tool ->
                when {
                    tool.isTool() -> add(tool.asTool().name())
                    tool.isWebSearchTool20250305() -> add(OfficialToolIds.WEB_SEARCH)
                }
            }
        }
        val toAppend = specs.filterNot { it.toolId in existing }.map { it.tool }
        return if (toAppend.isEmpty()) tools else tools + toAppend
    }
}
