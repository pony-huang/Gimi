package github.ponyhuang.gimi.data.agent.recommendation

import android.util.Log
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCallingConfig
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.ToolConfig
import github.ponyhuang.gimi.data.agent.AgentContributionRegistry
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.data.agent.AgentPrompts
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.ModelConfig
import github.ponyhuang.gimi.data.agent.recommendation.AgentRecommendationGenerator.Companion.READ_ONLY_STATUS_TOOLS
import github.ponyhuang.gimi.data.agent.toRuntimeMetadata
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationGenerationInput
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSnapshot
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationGenerator
import github.ponyhuang.gimi.pluginapi.PluginJson
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 动态构建推荐Prompt。 */
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
        // 推荐只请求 json_object，不依赖服务端 Schema 支持；结构契约必须写进提示，
        // 避免模型自行编造键名，并由本地解析器拒绝不合规结果。
        appendLine("Respond with a single JSON object and nothing else, in exactly this shape:")
        appendLine(OUTPUT_SHAPE)
        appendLine("The \"recommendations\" array must contain exactly ${RecommendationSnapshot.RECOMMENDATION_COUNT} objects, each with a non-empty \"prompt\" and a \"category\" that is one of: ${CATEGORIES}.")
        appendLine("Use the user's locale and only capabilities supported by the information above.")
        appendLine("Keep each prompt to one short, natural sentence expressing a single core task. For Chinese, aim for 12–20 characters and never exceed 24 characters including punctuation; for other languages, use at most 12 words.")
        appendLine("Include only context essential to identify the task. Omit background explanations, inferred motivations, step-by-step instructions, lists, and detailed output requirements. Do not add greetings or introductory phrases.")
        appendLine("Examples of concise Chinese prompts: 找一家附近适合晚餐的餐厅；规划明早去公司的路线；把会议记录整理成待办清单。")
        // 引导模型结合时间/位置/最近应用等上下文推测用户当前与接下来的日常活动。
        appendLine(
            "Use the context above (especially localDateTime, timeZone, cachedLocation, and recentForegroundApp) " +
                    "to infer the user's likely current or next everyday activity, such as eating near meal times, " +
                    "commuting, working, resting, or being out and about, and turn the most helpful of these " +
                    "predictions into concrete tasks. Keep predictions realistic and grounded in ordinary daily life.",
        )
        appendLine("Prefer meaningful tasks that produce a useful result, save effort, or support a decision.")
        appendLine("Do not recommend querying directly visible status such as battery level, current time, or network state.")
        appendLine("Prefer useful tasks over trivial lookups, generic greetings, or redundant actions. A task may require multiple steps to execute, but its prompt must only state the goal concisely; leave execution details for the assistant after the user sends it.")
    }

    /** 最小结构示例，由 [RecommendationOutputParser] 校验。 */
    private const val OUTPUT_SHAPE: String =
        """{"recommendations":[{"prompt":"<task>","category":"<category>"}]}"""

    /** 提示中的类别名与本地枚举校验同源。 */
    private val CATEGORIES: String = RecommendationCategory.entries.joinToString(", ") { it.name.lowercase() }
}

/** 兼容的 JSON Object 模式。 */
object RecommendationOutputFormat {
    // 官方后端会把 responseSchema 升级成严格 json_schema；兼容服务未必支持该模式。
    // 这里只指定 MIME，保持替换官方组件前的 json_object 请求契约。
    val config = GenerateContentConfig(responseMimeType = "application/json")
}

/**
 * 推荐模型返回的顶层 JSON 对象。
 *
 * 必填字段刻意不给默认值：缺键或类型不符必须由序列化直接拒绝，
 * 而不是被兜底分支悄悄修好后掩盖提示契约已被破坏。
 */
@Serializable
private data class RecommendationResponse(
    val recommendations: List<RecommendationPayload>,
)

/**
 * 模型输出中的单条推荐。
 *
 * @property prompt 任务文案，长度上限由领域规则校验。
 * @property category 受控类别的字面量；保持字符串以便由领域枚举显式判定未知值，
 *   不让 domain 枚举反向依赖序列化注解。
 */
@Serializable
private data class RecommendationPayload(
    val prompt: String,
    val category: String,
)

/** 把模型的受控 JSON 输出转换为经过领域校验的推荐列表。 */
object RecommendationOutputParser {
    fun parse(raw: String): List<AgentRecommendation> {
        val response = runCatching { json.decodeFromString<RecommendationResponse>(raw) }
            .getOrElse {
                throw IllegalArgumentException(
                    "Recommendation model returned invalid or unexpected JSON.",
                    it
                )
            }
        require(response.recommendations.size == RecommendationSnapshot.RECOMMENDATION_COUNT) {
            "The recommendation model must return exactly ${RecommendationSnapshot.RECOMMENDATION_COUNT} items."
        }
        return response.recommendations.mapIndexed { index, payload ->
            val prompt = payload.prompt.trim()
            require(prompt.isNotEmpty() && prompt.length <= MAX_PROMPT_LENGTH) {
                "Recommendation prompts must contain 1..$MAX_PROMPT_LENGTH characters."
            }
            val category = runCatching {
                RecommendationCategory.valueOf(payload.category.uppercase())
            }.getOrElse {
                throw IllegalArgumentException(
                    "Unknown recommendation category.",
                    it
                )
            }
            AgentRecommendation("recommendation-${index + 1}", prompt, category)
        }
    }

    private const val MAX_PROMPT_LENGTH: Int = 160

    // 模型可能附带额外字段：未知键忽略，必填与类型仍然严格失败。
    private val json = Json { ignoreUnknownKeys = true }
}

/** 推荐生成只依据能力摘要，不执行能力工具，避免工具结果污染推荐会话。 */
object RecommendationToolResultSanitizer {
    fun sanitize(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associate { (key, nested) ->
            key.toString() to sanitize(nested)
        }

        is Iterable<*> -> value.map(::sanitize)
        else -> PluginJson.toNative(value)
    }
}

private class JsonNativeTool(
    private val delegate: BaseTool,
) : BaseTool(delegate.name, delegate.description) {
    override fun declaration() = delegate.declaration()

    override suspend fun run(
        context: com.google.adk.kt.tools.ToolContext,
        args: Map<String, Any?>,
    ): Any = RecommendationToolResultSanitizer.sanitize(delegate.run(context, args))
        ?: emptyMap<String, Any>()
}

/** 使用快速模型优先策略执行无工具、无会话的推荐生成请求。 */
@Singleton
class AgentRecommendationGenerator @Inject constructor(
    private val modelFactory: AgentLLMModelFactory,
    private val contributionRegistry: AgentContributionRegistry,
) : RecommendationGenerator {
    override suspend fun generate(input: RecommendationGenerationInput): List<AgentRecommendation> {
        val resolvedInput =
            input.copy(systemInstruction = AgentPrompts.defaultAssistantInstruction())
        val config = modelFactory.forRecommendationJson(
            modelFactory.selectFastModelConfig()
                ?: error("Configure an OpenAI-compatible fast model for recommendation generation."),
        )
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
                temperature = 0.9f,
                maxOutputTokens = 1_024,
                thinkingConfig = ThinkingConfig(false),
                responseMimeType = RecommendationOutputFormat.config.responseMimeType,
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
        return try {
            RecommendationOutputParser.parse(raw)
        } catch (error: IllegalArgumentException) {
            // 模型原文是唯一能区分"键名不符"与"条数不足"的证据；推荐文案由已授权上下文派生，
            // 只记录截断片段，且失败信息保持干净，原始细节不进 UI。
            Log.w(
                TAG,
                "Recommendation output rejected (model=${config.modelId}, responses=${responses.size}, " +
                    "chars=${raw.length}); raw=${raw.take(MAX_LOGGED_RAW_LENGTH)}",
                error,
            )
            throw error
        }
    }

    /**
     * 经贡献方注册表聚合全部来源的扁平工具目录，与 Agent 构建共用同一套配置源；
     * 推荐会话实际允许的函数由 [READ_ONLY_STATUS_TOOLS] 在 ToolConfig 层约束。
     */
    private suspend fun allRecommendationTools(config: ModelConfig): List<BaseTool> =
        contributionRegistry
            .toolCatalog(AgentToolCatalogContext(modelRuntime = config.toRuntimeMetadata()))
            .flatMap { entry -> entry.tools }
            .distinctBy { it.name }
            .map(::JsonNativeTool)

    private companion object {
        const val TAG: String = "RecommendationGen"

        /** 失败日志中保留的模型原文上限，足以看清顶层键名与首条结构。 */
        const val MAX_LOGGED_RAW_LENGTH: Int = 500

        val RECOMMENDATION_INSTRUCTION = """
            Generate safe, varied suggestions only. Do not execute tools or claim that an action happened.
            Use the authorized read-only context to anticipate everyday life: infer what the user is likely
            doing right now and what they will plausibly do next from local date, time of day, weekday or
            weekend, location hints, battery and network state, and the recent foreground app. For example,
            around meal times suggest dining-related help, being away from home may suggest meals, errands,
            or navigation, late evening suggests winding down, and workday mornings suggest planning the day.
            Keep every inference grounded in ordinary daily routines; never invent context the data does not
            support, and only recommend tasks the available capabilities can actually complete.
            Avoid trivial queries for directly visible status (for example battery level, current time, or
            network state), generic greetings, and redundant actions. Prefer concrete multi-step tasks that
            save effort or support a decision. Return exactly the requested JSON object without Markdown or
            explanations.
        """.trimIndent()


        /** 推荐生成允许使用的只读工具，用于获取当前状态和了解可用技能。 */
        val READ_ONLY_STATUS_TOOLS = listOf(
            "get_current_location",
            "get_current_time",
            "list_installed_apps",
            "get_upcoming_calendar_events",
            "list_calendars",
            "list_skills",
            "load_skill",
            "load_skill_resource",
        )
    }
}
