package github.ponyhuang.asssistantai.agent.tools.dynamic

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val TOOL_SEARCH_NAME: String = "tool_search"

/**
 * 单次模型请求可动态暴露的工具声明预算。
 *
 * @property maxTools 最多同时暴露的业务工具数量。
 * @property maxSchemaBytes 工具声明序列化为 UTF-8 JSON 后允许的总字节数。
 */
internal data class ToolAccessBudget(
    val maxTools: Int = 8,
    val maxSchemaBytes: Int = 16 * 1024,
)

/**
 * 可供动态检索的一个工具来源。
 *
 * 来源只负责在本机解析 [BaseTool]；是否向模型暴露以及失败隔离由
 * [ToolSearchToolset] 统一处理。来源自身可以按 invocation 上下文
 * （RunConfig metadata）做会话级过滤。
 */
internal interface DynamicToolCandidateSource {
    val id: String
    val displayName: String

    suspend fun loadTools(readonlyContext: ReadonlyContext?): List<BaseTool>
}

/**
 * Tool search 网关 —— 参考 Kimi「动态加载工具」设计：核心工具固定声明，
 * 大量业务工具推迟到模型调用 `tool_search` 后再注入。
 *
 * `LlmAgentTurn` 在每次模型调用前都会重新调用 [getTools]。`tool_search` 命中后
 * 选中的工具名会写入 session state（[STATE_KEY_LOADED_TOOLS]），后续请求 —— 包括
 * 新的用户轮次 —— 都继续携带这些工具声明，保持请求前缀稳定（前缀缓存友好），
 * 模型也不需要每轮重新检索。确认恢复时同样从持久化 state 重建选择，不依赖
 * 进程内状态。
 */
internal class ToolSearchToolset(
    private val mode: ToolAccessMode,
    private val sources: List<DynamicToolCandidateSource>,
    private val budget: ToolAccessBudget = ToolAccessBudget(),
) : Toolset {
    private val discoveryMutex = Mutex()
    private val successfulSourceCache = mutableMapOf<String, List<BaseTool>>()
    private val autoOnDemandInvocations = ConcurrentHashMap.newKeySet<String>()
    private val searchTool = ToolSearchTool(this)

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val selectedNames = latestSelection(readonlyContext)
        if (selectedNames != null) {
            return listOf(searchTool) + resolveSelectedTools(selectedNames, readonlyContext)
        }

        return when (mode) {
            ToolAccessMode.ON_DEMAND -> listOf(searchTool)
            ToolAccessMode.ALWAYS_AVAILABLE ->
                discover(readonlyContext).uniqueCandidates.map(ToolCandidate::tool)
            ToolAccessMode.AUTO -> automaticTools(readonlyContext)
        }
    }

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = llmRequest

    internal suspend fun search(
        rawQuery: String,
        toolContext: ToolContext,
    ): Map<String, Any> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return unchangedSearchResult()

        val discovery = discover(toolContext.context)
        val ranked = discovery.uniqueCandidates
            .mapNotNull { candidate ->
                searchScore(query, candidate)?.let { score -> candidate to score }
            }
            .sortedWith(
                compareByDescending<Pair<ToolCandidate, Int>> { it.second }
                    .thenBy { it.first.tool.name },
            )
            .map(Pair<ToolCandidate, Int>::first)

        val selected = selectWithinBudget(ranked)
        if (selected.isEmpty()) {
            return unchangedSearchResult(
                ambiguous = discovery.ambiguousCandidates(query),
                sourceErrors = discovery.sourceErrors,
            )
        }

        // 命中即持久化：写入 session state，后续请求（含新用户轮次）继续携带这些声明。
        toolContext.actions.stateDelta[STATE_KEY_LOADED_TOOLS] = selected.map { it.tool.name }

        return mapOf(
            KEY_LOADED_TOOLS to selected.map { it.summary() },
            KEY_OMITTED_MATCH_COUNT to ranked.size - selected.size,
            KEY_AMBIGUOUS_TOOLS to discovery.ambiguousCandidates(query),
            KEY_SOURCE_ERRORS to discovery.sourceErrors.map(SourceFailure::summary),
            KEY_SELECTION_CHANGED to true,
        )
    }

    private suspend fun automaticTools(
        readonlyContext: ReadonlyContext?,
    ): List<BaseTool> {
        val invocationId = readonlyContext?.invocationId ?: NO_INVOCATION_ID
        if (invocationId in autoOnDemandInvocations) return listOf(searchTool)

        val discovery = discover(readonlyContext)
        val candidates = discovery.uniqueCandidates
        val fitsBudget = candidates.size <= budget.maxTools &&
                candidates.sumOf(::schemaBytes) <= budget.maxSchemaBytes
        return if (discovery.sourceErrors.isEmpty() && fitsBudget) {
            candidates.map(ToolCandidate::tool)
        } else {
            autoOnDemandInvocations += invocationId
            listOf(searchTool)
        }
    }

    private suspend fun resolveSelectedTools(
        names: List<String>,
        readonlyContext: ReadonlyContext?,
    ): List<BaseTool> {
        val candidates = discover(readonlyContext).uniqueCandidates.associateBy { it.tool.name }
        return names.mapNotNull(candidates::get).map(ToolCandidate::tool)
    }

    private suspend fun discover(
        readonlyContext: ReadonlyContext?,
    ): Discovery = discoveryMutex.withLock {
        val candidates = mutableListOf<ToolCandidate>()
        val failures = mutableListOf<SourceFailure>()
        for (source in sources) {
            val cached = successfulSourceCache[source.id]
            val tools = cached
                ?: try {
                    source.loadTools(readonlyContext).also { loaded ->
                        successfulSourceCache[source.id] = loaded
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    failures += SourceFailure(source.id, source.displayName)
                    emptyList()
                }
            tools.forEach { tool ->
                if (tool.declaration() != null) {
                    candidates += ToolCandidate(source.id, source.displayName, tool)
                }
            }
        }
        Discovery.from(candidates, failures)
    }

    /**
     * 当前生效的工具选择：优先读 session state 中持久化的选择（跨轮保留）；
     * 无记录时回退到当前 invocation 的 `tool_search` 事件扫描（兼容 state delta
     * 尚未合并进 session 的窗口期）。
     */
    private suspend fun latestSelection(
        readonlyContext: ReadonlyContext?,
    ): List<String>? {
        if (readonlyContext == null) return null
        val persisted = readonlyContext.state[STATE_KEY_LOADED_TOOLS] as? List<*>
        val persistedNames = persisted?.filterIsInstance<String>().orEmpty()
        if (persistedNames.isNotEmpty()) return persistedNames
        val events = readonlyContext.getEvents(currentInvocation = true)
        return events.asReversed()
            .asSequence()
            .flatMap { event -> event.functionResponses().asReversed().asSequence() }
            .filter { response -> response.name == TOOL_SEARCH_NAME }
            .mapNotNull { response ->
                val changed = response.response[KEY_SELECTION_CHANGED] as? Boolean ?: false
                if (!changed) return@mapNotNull null
                response.response[KEY_LOADED_TOOLS].loadedToolNames()
            }
            .firstOrNull()
    }

    private fun selectWithinBudget(ranked: List<ToolCandidate>): List<ToolCandidate> {
        val selected = mutableListOf<ToolCandidate>()
        var usedBytes = 0
        for (candidate in ranked) {
            if (selected.size >= budget.maxTools) break
            val bytes = schemaBytes(candidate)
            if (selected.isEmpty() || usedBytes + bytes <= budget.maxSchemaBytes) {
                selected += candidate
                usedBytes += bytes
            }
        }
        return selected
    }

    private fun searchScore(
        rawQuery: String,
        candidate: ToolCandidate,
    ): Int? {
        val query = normalize(rawQuery)
        val name = normalize(candidate.tool.name.replace('_', ' '))
        val description = normalize(candidate.tool.description)
        val source = normalize(candidate.sourceDisplayName)
        val tokens = query.split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) return null

        var score = when {
            name == query -> 10_000
            name.startsWith(query) -> 5_000
            query in name -> 3_000
            else -> 0
        }
        score += tokens.count { token -> token in name } * 500
        score += tokens.count { token -> token in description } * 100
        score += tokens.count { token -> token in source } * 25
        return score.takeIf { it > 0 }
    }

    private fun schemaBytes(candidate: ToolCandidate): Int =
        candidate.tool.declaration()
            ?.let { declaration ->
                Json.encodeToString(FunctionDeclaration.serializer(), declaration)
                    .encodeToByteArray()
                    .size
            }
            ?: 0

    private fun unchangedSearchResult(
        ambiguous: List<Map<String, Any>> = emptyList(),
        sourceErrors: List<SourceFailure> = emptyList(),
    ): Map<String, Any> = mapOf(
        KEY_LOADED_TOOLS to emptyList<Map<String, Any>>(),
        KEY_OMITTED_MATCH_COUNT to 0,
        KEY_AMBIGUOUS_TOOLS to ambiguous,
        KEY_SOURCE_ERRORS to sourceErrors.map(SourceFailure::summary),
        KEY_SELECTION_CHANGED to false,
    )

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(SEARCH_SEPARATOR, " ")
            .trim()
            .replace(MULTIPLE_SPACES, " ")

    private fun Discovery.ambiguousCandidates(query: String): List<Map<String, Any>> =
        ambiguousByName.entries
            .filter { (name, candidates) ->
                searchScore(query, candidates.first()) != null || normalize(query) in normalize(name)
            }
            .map { (name, candidates) ->
                mapOf(
                    "name" to name,
                    "sources" to candidates.map(ToolCandidate::sourceDisplayName).distinct(),
                )
            }

    private fun Any?.loadedToolNames(): List<String> =
        (this as? List<*>).orEmpty().mapNotNull { item ->
            (item as? Map<*, *>)?.get("name") as? String
        }

    private class ToolSearchTool(
        private val owner: ToolSearchToolset,
    ) : BaseTool(
        name = TOOL_SEARCH_NAME,
        description = TOOL_SEARCH_DESCRIPTION,
    ) {
        override fun declaration(): FunctionDeclaration = FunctionDeclaration(
            name = name,
            description = description,
            parameters = Schema(
                type = Type.OBJECT,
                properties = mapOf(
                    ARG_QUERY to Schema(
                        type = Type.STRING,
                        description = "Natural-language capability or concise English keywords.",
                    ),
                ),
                required = listOf(ARG_QUERY),
            ),
        )

        override suspend fun run(
            context: ToolContext,
            args: Map<String, Any>,
        ): Any = owner.search(
            rawQuery = args[ARG_QUERY] as? String ?: "",
            toolContext = context,
        )
    }

    internal companion object {
        /** session state 中持久化 `tool_search` 选中工具名的 key。 */
        const val STATE_KEY_LOADED_TOOLS: String = "selkie.tool_search.loaded_tools"

        private const val NO_INVOCATION_ID = "__no_invocation__"
        private const val ARG_QUERY = "query"
        private const val KEY_LOADED_TOOLS = "loaded_tools"
        private const val KEY_OMITTED_MATCH_COUNT = "omitted_match_count"
        private const val KEY_AMBIGUOUS_TOOLS = "ambiguous_tools"
        private const val KEY_SOURCE_ERRORS = "source_errors"
        private const val KEY_SELECTION_CHANGED = "selection_changed"
        private const val MAX_DESCRIPTION_LENGTH = 240
        private val SEARCH_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
        private val MULTIPLE_SPACES = Regex("\\s+")
        private const val TOOL_SEARCH_DESCRIPTION =
            "Searches enabled local, MCP, and formula tools by capability. Matching tool " +
                "definitions become available in the next model step."
    }

    /**
     * 已发现的单个可调用工具及其来源信息。
     *
     * @property sourceId 来源的稳定 ID。
     * @property sourceDisplayName 可安全返回给模型的来源名称。
     * @property tool 本机保存的 ADK 执行实例。
     */
    private data class ToolCandidate(
        val sourceId: String,
        val sourceDisplayName: String,
        val tool: BaseTool,
    ) {
        fun summary(): Map<String, Any> = mapOf(
            "name" to tool.name,
            "description" to tool.description.take(MAX_DESCRIPTION_LENGTH),
            "source" to sourceDisplayName,
        )
    }

    /**
     * 一次工具来源发现失败的脱敏记录。
     *
     * @property sourceId 来源稳定 ID。
     * @property sourceDisplayName 可安全展示的来源名称。
     */
    private data class SourceFailure(
        val sourceId: String,
        val sourceDisplayName: String,
    ) {
        fun summary(): Map<String, Any> = mapOf(
            "source" to sourceDisplayName,
            "message" to "Tool source is temporarily unavailable.",
        )
    }

    /**
     * 一次候选发现的去重结果。
     *
     * @property uniqueCandidates 名称唯一、可安全暴露的工具。
     * @property ambiguousByName 发生名称冲突、必须拒绝暴露的工具。
     * @property sourceErrors 本次发现失败的来源。
     */
    private data class Discovery(
        val uniqueCandidates: List<ToolCandidate>,
        val ambiguousByName: Map<String, List<ToolCandidate>>,
        val sourceErrors: List<SourceFailure>,
    ) {
        companion object {
            fun from(
                candidates: List<ToolCandidate>,
                failures: List<SourceFailure>,
            ): Discovery {
                val byName = candidates.groupBy { it.tool.name }
                return Discovery(
                    uniqueCandidates = byName.values
                        .filter { it.size == 1 }
                        .map(List<ToolCandidate>::single),
                    ambiguousByName = byName.filterValues { it.size > 1 },
                    sourceErrors = failures,
                )
            }
        }
    }
}
