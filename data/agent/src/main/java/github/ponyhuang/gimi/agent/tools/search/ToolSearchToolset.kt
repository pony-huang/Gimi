package github.ponyhuang.gimi.agent.tools.search

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.agent.tools.modelRuntimeMetadataOrNull
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * 来源分别暴露完整目录和当前启用目录。向量同步始终使用 [loadAllTools]，只有
 * 最近邻搜索完成后才调用 [loadEnabledTools] 应用当前会话开关与授权。
 */
internal interface ToolCandidateSource {
    val id: String
    val displayName: String

    suspend fun loadAllTools(readonlyContext: ReadonlyContext?): List<BaseTool>

    suspend fun loadEnabledTools(readonlyContext: ReadonlyContext?): List<BaseTool>
}

/**
 * Tool search 网关
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
    private val sources: List<ToolCandidateSource>,
    private val vectorSearch: ToolVectorSearch,
    private val budget: ToolAccessBudget = ToolAccessBudget(),
) : Toolset {
    private val discoveryMutex = Mutex()
    private val allToolsSourceCache = mutableMapOf<String, List<BaseTool>>()
    private val searchTool = ToolSearchTool(this)

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val selectedNames = latestSelection(readonlyContext)
        if (selectedNames != null) {
            return listOf(searchTool) + resolveSelectedTools(selectedNames, readonlyContext)
        }

        return when (mode) {
            ToolAccessMode.ON_DEMAND -> listOf(searchTool)
            ToolAccessMode.ALWAYS_AVAILABLE ->
                discoverEnabled(readonlyContext).uniqueCandidates.map(ToolCandidate::tool)
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

        val allTools = discoverAll(toolContext.context)
        val enabledTools = discoverEnabled(toolContext.context)
        val enabledByKey = enabledTools.uniqueCandidates.associateBy(ToolCandidate::key)
        val matches = vectorSearch.search(
            scopeKey = vectorScopeKey(toolContext.context),
            documents = allTools.allCandidates.map(ToolCandidate::vectorDocument),
            query = query,
            // 过滤发生在搜索之后；取回完整排序才能保证关闭项不会挤掉启用项。
            maxResultCount = allTools.allCandidates.size,
        )
        val ranked = matches.mapNotNull { match -> enabledByKey[match.key] }

        val selected = selectWithinBudget(ranked)
        if (selected.isEmpty()) {
            return unchangedSearchResult(
                ambiguous = allTools.ambiguousCandidates(matches.mapTo(hashSetOf(), ToolVectorMatch::key)),
                sourceErrors = (allTools.sourceErrors + enabledTools.sourceErrors)
                    .distinctBy(SourceFailure::sourceId),
            )
        }

        // 命中即持久化：写入 session state，后续请求（含新用户轮次）继续携带这些声明。
        toolContext.actions.stateDelta[STATE_KEY_LOADED_TOOLS] = selected.map { it.tool.name }

        return mapOf(
            KEY_LOADED_TOOLS to selected.map { it.summary() },
            KEY_OMITTED_MATCH_COUNT to ranked.size - selected.size,
            KEY_AMBIGUOUS_TOOLS to allTools.ambiguousCandidates(
                matches.mapTo(hashSetOf(), ToolVectorMatch::key),
            ),
            KEY_SOURCE_ERRORS to (allTools.sourceErrors + enabledTools.sourceErrors)
                .distinctBy(SourceFailure::sourceId)
                .map(SourceFailure::summary),
            KEY_SELECTION_CHANGED to true,
        )
    }

    private suspend fun resolveSelectedTools(
        names: List<String>,
        readonlyContext: ReadonlyContext?,
    ): List<BaseTool> {
        val candidates = discoverEnabled(readonlyContext)
            .uniqueCandidates
            .associateBy { it.tool.name }
        return names.mapNotNull(candidates::get).map(ToolCandidate::tool)
    }

    private suspend fun discoverAll(
        readonlyContext: ReadonlyContext?,
    ): Discovery = discoveryMutex.withLock {
        discoverSources(readonlyContext, useAllTools = true)
    }

    private suspend fun discoverEnabled(
        readonlyContext: ReadonlyContext?,
    ): Discovery = discoveryMutex.withLock {
        discoverSources(readonlyContext, useAllTools = false)
    }

    private suspend fun discoverSources(
        readonlyContext: ReadonlyContext?,
        useAllTools: Boolean,
    ): Discovery {
        val candidates = mutableListOf<ToolCandidate>()
        val failures = mutableListOf<SourceFailure>()
        for (source in sources) {
            val cached = allToolsSourceCache[source.id].takeIf { useAllTools }
            val tools = cached
                ?: try {
                    val loaded = if (useAllTools) {
                        source.loadAllTools(readonlyContext)
                    } else {
                        source.loadEnabledTools(readonlyContext)
                    }
                    loaded.also {
                        if (useAllTools) allToolsSourceCache[source.id] = it
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    failures += SourceFailure(source.id, source.displayName)
                    emptyList()
                }
            tools.forEach { tool ->
                if (tool.declaration() != null) {
                    candidates += ToolCandidate(
                        sourceId = source.id,
                        sourceDisplayName = source.displayName,
                        tool = tool,
                    )
                }
            }
        }
        return Discovery.from(candidates, failures)
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

    private fun vectorScopeKey(readonlyContext: ReadonlyContext?): String {
        val runtime = readonlyContext.modelRuntimeMetadataOrNull()
        return buildString {
            append("sources:")
            append(sources.joinToString(separator = ",", transform = ToolCandidateSource::id))
            if (runtime != null) {
                append("|service:")
                append(runtime.serviceId)
                append("|model:")
                append(runtime.modelId)
            }
        }
    }

    private fun Discovery.ambiguousCandidates(
        matchedKeys: Set<String>,
    ): List<Map<String, Any>> =
        ambiguousByName.entries
            .filter { (_, candidates) ->
                candidates.any { candidate -> candidate.key in matchedKeys }
            }
            .map { (name, candidates) ->
                buildMap {
                    put("name", name)
                    put("sources", candidates.map(ToolCandidate::sourceDisplayName).distinct())
                }
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
            args: Map<String, Any?>,
        ): Any = owner.search(
            rawQuery = args[ARG_QUERY] as? String ?: "",
            toolContext = context,
        )
    }

    internal companion object {
        /** session state 中持久化 `tool_search` 选中工具名的 key。 */
        const val STATE_KEY_LOADED_TOOLS: String = "selkie.tool_search.loaded_tools"

        private const val ARG_QUERY = "query"
        private const val KEY_LOADED_TOOLS = "loaded_tools"
        private const val KEY_OMITTED_MATCH_COUNT = "omitted_match_count"
        private const val KEY_AMBIGUOUS_TOOLS = "ambiguous_tools"
        private const val KEY_SOURCE_ERRORS = "source_errors"
        private const val KEY_SELECTION_CHANGED = "selection_changed"
        private const val MAX_DESCRIPTION_LENGTH = 240
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
        private val declarationJson: String = Json.encodeToString(
            FunctionDeclaration.serializer(),
            requireNotNull(tool.declaration()),
        )
        val key: String = "$sourceId:${tool.name}:${sha256(declarationJson).take(KEY_HASH_LENGTH)}"

        fun vectorDocument(): ToolVectorDocument {
            return ToolVectorDocument(
                key = key,
                text = buildString {
                    appendLine("Tool: ${tool.name}")
                    appendLine("Description: ${tool.description}")
                    append("Input schema: $declarationJson")
                },
            )
        }

        fun summary(): Map<String, Any> = buildMap {
            put("name", tool.name)
            put("description", tool.description.take(MAX_DESCRIPTION_LENGTH))
            put("source", sourceDisplayName)
        }

        private companion object {
            const val KEY_HASH_LENGTH: Int = 16
        }
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
     * @property allCandidates 来源返回的全部可声明工具；无论是否重名都写入向量索引。
     * @property uniqueCandidates 名称唯一、可安全暴露的工具。
     * @property ambiguousByName 发生名称冲突、必须拒绝暴露的工具。
     * @property sourceErrors 本次发现失败的来源。
     */
    private data class Discovery(
        val allCandidates: List<ToolCandidate>,
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
                    allCandidates = candidates,
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

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
