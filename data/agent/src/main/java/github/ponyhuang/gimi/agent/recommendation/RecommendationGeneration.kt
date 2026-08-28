package github.ponyhuang.gimi.agent.recommendation

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.FunctionCallingConfig
import com.google.adk.kt.types.ToolConfig
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.agent.AgentPrompts
import github.ponyhuang.gimi.agent.LocalToolCatalog
import github.ponyhuang.gimi.agent.ModelConfig
import github.ponyhuang.gimi.agent.McpToolsetRegistry
import github.ponyhuang.gimi.agent.toRuntimeMetadata
import github.ponyhuang.gimi.data.plugin.PluginManager
import github.ponyhuang.gimi.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationGenerationInput
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSnapshot
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject

/** 构建不含凭据与工具参数 schema 的推荐生成提示。 */
object RecommendationPromptBuilder {
    fun build(input: RecommendationGenerationInput): String = buildString {
        appendLine("Current assistant system instruction:")
        appendLine(input.systemInstruction)
        appendLine()
        appendLine("Available tool capabilities for recommendations (including the configured MCP catalog):")
        input.capabilities.forEach { capability ->
            appendLine("- [${capability.source}] ${capability.id}: ${capability.description}")
        }
        appendLine()
        appendLine("Current authorized read-only context:")
        input.context.values.toSortedMap().forEach { (key, value) ->
            appendLine("- $key: $value")
        }
        appendLine()
        appendLine("Generate exactly ${RecommendationSnapshot.RECOMMENDATION_COUNT} distinct tasks the user can send directly to this assistant.")
        appendLine("Use the user's locale and only capabilities supported by the information above.")
        appendLine("Prefer meaningful tasks that produce a useful result, save effort, or support a decision.")
        appendLine("Do not recommend querying directly visible status such as battery level, current time, or network state.")
        appendLine("Prefer concrete multi-step assistance over trivial lookups, generic greetings, or redundant actions.")
    }
}

/** 强制推荐模型返回稳定的 JSON 对象，避免 Markdown fence 或额外解释混入结果。 */
object RecommendationOutputFormat {
    val config = GenerateContentConfig(
        responseMimeType = "application/json",
        responseSchema = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "recommendations" to Schema(
                    type = Type.ARRAY,
                    items = Schema(
                        type = Type.OBJECT,
                        properties = mapOf(
                            "prompt" to Schema(type = Type.STRING),
                            "category" to Schema(
                                type = Type.STRING,
                                enum = RecommendationCategory.entries.map { it.name.lowercase() },
                            ),
                        ),
                        required = listOf("prompt", "category"),
                    ),
                ),
            ),
            required = listOf("recommendations"),
        ),
    )
}

/** 把模型的受控 JSON 输出转换为经过领域校验的推荐列表。 */
object RecommendationOutputParser {
    fun parse(raw: String): List<AgentRecommendation> {
        val normalized = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val array = when (normalized.firstOrNull()) {
            '[' -> JSONArray(normalized)
            '{' -> JSONObject(normalized).getJSONArray("recommendations")
            else -> throw IllegalArgumentException("Recommendation model returned invalid JSON.")
        }
        require(array.length() == RecommendationSnapshot.RECOMMENDATION_COUNT) {
            "The recommendation model must return exactly ${RecommendationSnapshot.RECOMMENDATION_COUNT} items."
        }
        val items = buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                // 兼容部分模型忽略 responseSchema 后返回的 task/suggestion 数组。
                val prompt = item.optString("prompt").trim()
                    .ifEmpty { item.optString("task").trim() }
                require(prompt.isNotEmpty() && prompt.length <= MAX_PROMPT_LENGTH) {
                    "Recommendation prompts must contain 1..$MAX_PROMPT_LENGTH characters."
                }
                val category = runCatching {
                    RecommendationCategory.valueOf(
                        item.optString("category", RecommendationCategory.GENERAL.name).uppercase(),
                    )
                }.getOrElse { throw IllegalArgumentException("Unknown recommendation category.", it) }
                add(AgentRecommendation("recommendation-${index + 1}", prompt, category))
            }
        }
        RecommendationSnapshot(items, 0L)
        return items
    }

    private const val MAX_PROMPT_LENGTH: Int = 160
}

/** 使用快速模型优先策略执行无工具、无会话的推荐生成请求。 */
@Singleton
class AgentRecommendationGenerator @Inject constructor(
    private val modelFactory: AgentLLMModelFactory,
    private val localToolCatalog: LocalToolCatalog,
    private val pluginManager: PluginManager,
    private val mcpToolsetRegistry: McpToolsetRegistry,
    private val officialToolsets: Set<@JvmSuppressWildcards OfficialToolset>,
) : RecommendationGenerator {
    override suspend fun generate(input: RecommendationGenerationInput): List<AgentRecommendation> {
        val resolvedInput = input.copy(systemInstruction = AgentPrompts.defaultAssistantInstruction())
        val config = modelFactory.selectFastModelConfig() ?: modelFactory.selectModelConfig(null)
        val model = modelFactory.createModel(config)
        val request = LlmRequest(
                model = model,
                contents = listOf(
                    Content(
                        role = Role.USER,
                        parts = listOf(Part(text = RecommendationPromptBuilder.build(resolvedInput))),
                    ),
                ),
                config = GenerateContentConfig(
                    systemInstruction = Content(
                        parts = listOf(
                            Part(text = AgentPrompts.defaultAssistantInstruction()),
                            Part(text = RECOMMENDATION_INSTRUCTION),
                        ),
                    ),
                    temperature = 0.7f,
                    maxOutputTokens = 1_024,
                    thinkingConfig = ThinkingConfig(false),
                    responseMimeType = RecommendationOutputFormat.config.responseMimeType,
                    responseSchema = RecommendationOutputFormat.config.responseSchema,
                    toolConfig = ToolConfig(
                        functionCallingConfig = FunctionCallingConfig(
                            allowedFunctionNames = READ_ONLY_STATUS_TOOLS,
                        ),
                    ),
                ),
            ).appendTools(allRecommendationTools(config))
        val responses = model.generateContent(request).toList()
        responses.firstOrNull { !it.errorMessage.isNullOrBlank() }?.errorMessage?.let { message ->
            error("Recommendation model request failed: $message")
        }
        fun responseText(index: Int): String? = responses[index].content?.parts
            ?.filter { it.thought != true }
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.takeIf(String::isNotBlank)
        val raw = responses.indices.reversed()
            .firstOrNull { !responses[it].partial && responseText(it) != null }
            ?.let(::responseText)
            ?: responses.indices.filter { responses[it].partial }
                .mapNotNull(::responseText)
                .joinToString("")
                .takeIf(String::isNotBlank)
            ?: error("Recommendation model returned no text.")
        return RecommendationOutputParser.parse(raw)
    }

    private suspend fun allRecommendationTools(config: ModelConfig) =
        buildList {
            addAll(localToolCatalog.tools())
            addAll(pluginManager.enabledPluginTools())
            pluginManager.enabledPluginToolsets().forEach { toolset ->
                runCatching { addAll(toolset.getTools(null)) }
            }
            runCatching {
                mcpToolsetRegistry.resolveAll().handles.forEach { handle ->
                    runCatching { addAll(handle.toolset.getTools(null)) }
                }
            }
            runCatching {
                val runtime = config.toRuntimeMetadata()
                officialToolsets.forEach { toolset ->
                    runCatching { addAll(toolset.resolveTools(runtime, null)) }
                }
            }
        }.distinctBy { it.name }

    private companion object {
        val RECOMMENDATION_INSTRUCTION = """
            Generate safe, varied suggestions only. Do not execute tools or claim that an action happened.
            Recommendations must be meaningful and useful in everyday workflows. Avoid trivial queries for
            directly visible status (for example battery level, current time, or network state), generic greetings,
            and redundant actions. Prefer concrete multi-step tasks that save effort or support a decision.
            Return exactly the requested JSON object without Markdown or explanations.
        """.trimIndent()

        val READ_ONLY_STATUS_TOOLS = listOf(
            "get_current_location",
            "is_location_enabled",
            "get_current_time",
            "get_screen_brightness",
            "get_screen_timeout",
            "list_installed_apps",
        )
    }
}
