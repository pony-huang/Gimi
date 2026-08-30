package github.ponyhuang.gimi.data.agent.tools.mcp

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.tools.mcp.McpToolException.McpToolExecutionException
import github.ponyhuang.gimi.data.agent.tools.mcp.McpToolException.McpToolLoadingException
import io.modelcontextprotocol.kotlin.sdk.types.Annotations
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.Icon
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Progress
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Connects to one MCP server and exposes its tools as ADK [BaseTool] instances.
 *
 * Server tools are selected by [toolFilter]. Resource tools are owned by this adapter and remain
 * available whenever [useMcpResources] is enabled and the server reports the resource capability.
 */
class McpToolset
internal constructor(
    private val mcpSessionManager: SessionManager,
    private val toolFilter: ToolFilter? = null,
    private val headerProvider: (suspend (ReadonlyContext) -> Map<String, String>)? = null,
    private val useMcpResources: Boolean = false,
    private val maxMcpResourceLength: Int = DEFAULT_MAX_RESOURCE_LENGTH,
) : Toolset {

    private val toolsMutex = Mutex()
    private var cachedTools: LoadedTools? = null

    /** 由 [toolsMutex] 保护，动态 header 场景下也只记录一次能力缺失告警。 */
    private var warnedResourcesUnsupported = false

    /** 服务端工具与本地资源工具必须分开，过滤器只作用于前者。 */
    private class LoadedTools(
        val serverTools: List<BaseTool>,
        val resourceTools: List<BaseTool>,
    )

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
        val loaded = initAndGetTools(readonlyContext)
        return loaded.serverTools.filter { toolFilter.isToolSelected(it, readonlyContext) } +
            loaded.resourceTools
    }

    private suspend fun initAndGetTools(readonlyContext: ReadonlyContext?): LoadedTools =
        toolsMutex.withLock {
            if (headerProvider == null) {
                cachedTools ?: initToolsWithRetries(readonlyContext).also { cachedTools = it }
            } else {
                // 动态 header 可能对应不同租户，工具声明必须按调用上下文重新获取。
                initToolsWithRetries(readonlyContext)
            }
        }

    private suspend fun initToolsWithRetries(
        readonlyContext: ReadonlyContext?,
        times: Int = DEFAULT_RETRY_TIMES,
        delayMs: Long = DEFAULT_RETRY_DELAY_MS,
    ): LoadedTools {
        val headers = readonlyContext?.let { headerProvider?.invoke(it) } ?: emptyMap()
        var session: McpSession? = null
        for (attempt in 1..times) {
            try {
                session = mcpSessionManager.getSession(headers, stale = session)
                return loadTools(session, headers)
            } catch (e: Exception) {
                handleLoadError(e, attempt)
                if (attempt == times) {
                    throw McpToolLoadingException(LOAD_TOOLS_FAILURE_MESSAGE, e)
                }
                delay(delayMs)
            }
        }
        error("Exhausted retries without returning or throwing")
    }

    private suspend fun loadTools(
        session: McpSession,
        headers: Map<String, String>,
    ): LoadedTools {
        val toolsResponse = session.client.listTools(options = mcpSessionManager.requestOptions())
        val serverTools =
            toolsResponse.tools.map { tool ->
                McpTool(
                    name = tool.name,
                    description = tool.description ?: "",
                    mcpSchemaTool = tool,
                    mcpSessionManager = mcpSessionManager,
                    headers = headers,
                )
            }

        if (!useMcpResources) return LoadedTools(serverTools, emptyList())

        val resourceTools =
            listOf(
                ListMcpResourcesTool(this),
                LoadMcpResourceTool(this, maxMcpResourceLength),
                ListMcpResourceTemplatesTool(this),
            )
        if (session.client.serverCapabilities?.resources != null) {
            return LoadedTools(serverTools, resourceTools)
        }

        if (!warnedResourcesUnsupported) {
            warnedResourcesUnsupported = true
            logger.warn {
                "useMcpResources is enabled, but the MCP server did not report the resources " +
                    "capability, so ${resourceTools.joinToString { it.name }} are not exposed."
            }
        }
        return LoadedTools(serverTools, emptyList())
    }

    /** Returns every advertised resource name for the app's connection probe. */
    internal suspend fun listResourceNames(readonlyContext: ReadonlyContext? = null): List<String> =
        listAllResources(readonlyContext).map { it.name }

    /** Returns one page of MCP resources and its continuation cursor. */
    internal suspend fun listResources(
        cursor: String? = null,
        readonlyContext: ReadonlyContext? = null,
    ): McpResourceListing {
        val result = withSession(readonlyContext) { session -> session.listResourcesPage(cursor) }
        return McpResourceListing(
            resources = result.resources.map { it.toResourceInfo() },
            nextCursor = result.nextCursor,
        )
    }

    /** Follows all resource pages for unique-name resolution, with a hard safety bound. */
    internal suspend fun listAllResources(
        readonlyContext: ReadonlyContext? = null,
    ): List<McpResourceInfo> {
        val all = mutableListOf<McpResourceInfo>()
        var cursor: String? = null
        repeat(MAX_FULL_SCAN_PAGES) {
            val page = withSession(readonlyContext) { session -> session.listResourcesPage(cursor) }
            all += page.resources.map { it.toResourceInfo() }
            cursor = page.nextCursor ?: return all
        }
        throw McpToolExecutionException(
            "MCP server kept paginating resources/list past $MAX_FULL_SCAN_PAGES pages; " +
                "giving up rather than scanning forever.",
        )
    }

    /** Returns one page of resource templates and its continuation cursor. */
    internal suspend fun listResourceTemplates(
        cursor: String? = null,
        readonlyContext: ReadonlyContext? = null,
    ): McpResourceTemplateListing {
        val result = withSession(readonlyContext) { session -> session.listResourceTemplatesPage(cursor) }
        return McpResourceTemplateListing(
            resourceTemplates = result.resourceTemplates.map { it.toResourceTemplateInfo() },
            nextCursor = result.nextCursor,
        )
    }

    /** Reads [uri] and converts SDK records into the project-owned sealed content model. */
    internal suspend fun readResource(
        uri: String,
        readonlyContext: ReadonlyContext? = null,
    ): List<McpResourceContent> {
        val result = withSession(readonlyContext) { session -> session.readResource(uri) }
        return result.contents.map { it.toResourceContent() }
    }

    /**
     * Executes a resource request on a pooled session and replaces only sessions that actually
     * failed. Invalid arguments and resource-not-found responses describe a bad request, so they
     * must not evict a healthy shared session.
     */
    private suspend fun <T> withSession(
        readonlyContext: ReadonlyContext?,
        block: suspend (McpSession) -> T,
    ): T {
        val headers = readonlyContext?.let { headerProvider?.invoke(it) } ?: emptyMap()
        var stale: McpSession? = null
        for (attempt in 1..DEFAULT_RETRY_TIMES) {
            var session: McpSession? = null
            try {
                session = mcpSessionManager.getSession(headers, stale = stale)
                return block(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: McpException) {
                if (e.code == RPCError.ErrorCode.RESOURCE_NOT_FOUND) throw e
                if (attempt == DEFAULT_RETRY_TIMES) throw e
                stale = session
                logger.warn(e) { "Retrying MCP resource call, attempt $attempt: ${e.message}" }
                delay(DEFAULT_RETRY_DELAY_MS)
            } catch (e: Exception) {
                if (attempt == DEFAULT_RETRY_TIMES) throw e
                stale = session
                logger.warn(e) { "Retrying MCP resource call, attempt $attempt: ${e.message}" }
                delay(DEFAULT_RETRY_DELAY_MS)
            }
        }
        error("Exhausted retries without returning or throwing")
    }

    private suspend fun McpSession.listResourcesPage(
        cursor: String?,
    ): ListResourcesResult =
        client.listResources(
            request = ListResourcesRequest(PaginatedRequestParams(cursor)),
            options = mcpSessionManager.requestOptions(),
        )

    private suspend fun McpSession.listResourceTemplatesPage(
        cursor: String?,
    ): ListResourceTemplatesResult =
        client.listResourceTemplates(
            request = ListResourceTemplatesRequest(PaginatedRequestParams(cursor)),
            options = mcpSessionManager.requestOptions(),
        )

    private suspend fun McpSession.readResource(uri: String): ReadResourceResult =
        client.readResource(
            request = ReadResourceRequest(ReadResourceRequestParams(uri)),
            options = mcpSessionManager.requestOptions(),
        )

    private fun handleLoadError(e: Exception, attempt: Int) {
        when (e) {
            is CancellationException -> throw e
            is IllegalArgumentException -> {
                logger.error(e) { "Invalid argument encountered during tool loading." }
                throw McpToolLoadingException("Invalid argument encountered during tool loading.", e)
            }
        }
        logger.error(e) { "Unexpected error during tool loading, retry attempt $attempt" }
    }

    override fun close() {
        mcpSessionManager.close()
        cachedTools = null
    }

    companion object {
        private const val MAX_FULL_SCAN_PAGES = 100
        private const val DEFAULT_RETRY_TIMES = 3
        private const val DEFAULT_RETRY_DELAY_MS = 100L
        private const val DEFAULT_MAX_RESOURCE_LENGTH = 10000
        private const val LOAD_TOOLS_FAILURE_MESSAGE = "Failed to load tools."

        private val logger = LoggerFactory.getLogger(McpToolset::class)
    }

    /**
     * Configuration used to create an [McpToolset].
     *
     * @property stdioConnectionParams Local-process stdio connection settings.
     * @property sseConnectionParams SSE connection settings.
     * @property streamableHttpConnectionParams Streamable HTTP connection settings.
     * @property toolFilter Optional selector for server-advertised tools; resource tools are not
     *   filtered.
     * @property useMcpResources Whether to expose the three ADK resource tools when supported.
     * @property maxMcpResourceLength Maximum rendered characters per text resource content item.
     */
    data class McpToolsetConfig(
        val stdioConnectionParams: McpConnectionParameters.Stdio? = null,
        val sseConnectionParams: McpConnectionParameters.Sse? = null,
        val streamableHttpConnectionParams: McpConnectionParameters.StreamableHttp? = null,
        val toolFilter: ToolFilter? = null,
        val useMcpResources: Boolean = false,
        val maxMcpResourceLength: Int = DEFAULT_MAX_RESOURCE_LENGTH,
    ) {
        /** Creates the toolset and validates that exactly one transport was configured. */
        fun toToolset(
            headerProvider: (suspend (ReadonlyContext) -> Map<String, String>)? = null,
            progressConsumers: List<(Progress) -> Unit> = emptyList(),
        ): McpToolset {
            val params =
                listOfNotNull(
                    stdioConnectionParams,
                    sseConnectionParams,
                    streamableHttpConnectionParams,
                )
            require(params.size == 1) {
                "Exactly one of stdioConnectionParams, sseConnectionParams or " +
                    "streamableHttpConnectionParams must be set"
            }
            return McpToolset(
                McpSessionManager(params.single(), progressConsumers = progressConsumers),
                toolFilter,
                headerProvider,
                useMcpResources,
                maxMcpResourceLength,
            )
        }

        /** Creates a toolset with an injected session manager for tests and composition. */
        internal fun toToolset(
            sessionManager: SessionManager,
            headerProvider: (suspend (ReadonlyContext) -> Map<String, String>)? = null,
        ): McpToolset =
            McpToolset(
                sessionManager,
                toolFilter,
                headerProvider,
                useMcpResources,
                maxMcpResourceLength,
            )
    }
}

private fun Resource.toResourceInfo(): McpResourceInfo =
    McpResourceInfo(
        name = name,
        uri = uri,
        title = title,
        description = description,
        mimeType = mimeType,
        size = size,
        annotations = annotations?.toAnnotations(),
        meta = meta?.toNativeMap(),
        icons = icons.orEmpty().map { it.toIcon() },
    )

private fun ResourceTemplate.toResourceTemplateInfo(): McpResourceTemplateInfo =
    McpResourceTemplateInfo(
        name = name,
        uriTemplate = uriTemplate,
        title = title,
        description = description,
        mimeType = mimeType,
        annotations = annotations?.toAnnotations(),
        meta = meta?.toNativeMap(),
        icons = icons.orEmpty().map { it.toIcon() },
    )

private fun Annotations.toAnnotations(): McpAnnotations =
    McpAnnotations(
        audience = audience.orEmpty().map { McpRole(it.name.lowercase()) },
        priority = priority,
        lastModified = lastModified,
    )

private fun Icon.toIcon(): McpIcon =
    McpIcon(
        src = src,
        mimeType = mimeType,
        sizes = sizes.orEmpty(),
        theme = theme?.name?.lowercase(),
    )

private fun ResourceContents.toResourceContent(): McpResourceContent =
    when (this) {
        is TextResourceContents ->
            McpResourceContent.Text(
                uri = uri,
                mimeType = mimeType,
                text = text,
                meta = meta?.toNativeMap(),
            )
        is BlobResourceContents ->
            McpResourceContent.Blob(
                uri = uri,
                mimeType = mimeType,
                blobBase64 = blob,
                meta = meta?.toNativeMap(),
            )
        else -> error("Unsupported MCP resource content type: ${this::class.qualifiedName}")
    }

private fun JsonObject.toNativeMap(): Map<String, Any?> =
    mapValues { (_, value) -> value.toNativeValue() }

private fun JsonElement.toNativeValue(): Any? =
    when (this) {
        is JsonObject -> toNativeMap()
        is JsonArray -> map { it.toNativeValue() }
        is JsonNull -> null
        is JsonPrimitive ->
            if (isString) content
            else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
