package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import github.ponyhuang.asssistantai.domain.mcp.repository.McpRepository
import github.ponyhuang.asssistantai.core.common.concurrent.cancellationAwareRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Discovers remote MCP tools and exposes them as regular ADK tools. */
@Singleton
class McpToolRegistry @Inject constructor(
    private val servers: McpRepository,
) {
    private val discoveryMutex = Mutex()
    private var cachedKey: CacheKey? = null
    private var cachedTools: List<BaseTool> = emptyList()

    suspend fun tools(selectedServerIds: Set<String>? = null): List<BaseTool> =
        discoveryMutex.withLock {
        val key = CacheKey(servers.revision.value, selectedServerIds)
        if (key == cachedKey) return@withLock cachedTools
        val discovered = selectMcpServers(servers.currentServers(), selectedServerIds)
            .filter { it.endpointUrl.isNotBlank() }
            .flatMap { server ->
                cancellationAwareRunCatching { discover(server) }.getOrElse { emptyList() }
            }
            .distinctBy(BaseTool::name)
        cachedKey = key
        cachedTools = discovered
        discovered
    }

    private data class CacheKey(
        val revision: Long,
        val selectedServerIds: Set<String>?,
    )

    private suspend fun discover(server: McpServer): List<BaseTool> {
        val connection = connect(server)
        return try {
            connection.client.listTools().tools.mapNotNull { tool ->
                mcpToolName(server.id, tool.name)?.let { name ->
                    McpRemoteTool(server, tool, name)
                }
            }
        } finally {
            connection.close()
        }
    }

    private suspend fun connect(server: McpServer): McpConnection {
        val httpClient = HttpClient(OkHttp) { install(SSE) }
        val client = Client(Implementation(name = "asssistantai", version = "1.0"))
        val requestHeaders: HttpRequestBuilder.() -> Unit = { addAuthentication(server) }
        val transport = when (server.transport) {
            McpTransport.STREAMABLE_HTTP -> StreamableHttpClientTransport(
                client = httpClient,
                url = server.endpointUrl.trim(),
                requestBuilder = requestHeaders,
            )
            McpTransport.SSE -> SseClientTransport(
                client = httpClient,
                urlString = server.endpointUrl.trim(),
                requestBuilder = requestHeaders,
            )
        }
        try {
            client.connect(transport)
        } catch (failure: Throwable) {
            httpClient.close()
            throw failure
        }
        return McpConnection(client, httpClient, server)
    }

    private fun HttpRequestBuilder.addAuthentication(server: McpServer) {
        server.bearerToken.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        server.headers.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .forEach { line ->
                val (name, value) = line.split('=', limit = 2)
                if (name.isNotBlank()) header(name.trim(), value.trim())
            }
    }

    private class McpConnection(
        val client: Client,
        private val httpClient: HttpClient,
        private val server: McpServer,
    ) : AutoCloseable {
        override fun close() = httpClient.close()

        suspend fun call(tool: Tool, args: Map<String, Any>): Any {
            val result = client.callTool(tool.name, args)
            return buildMap {
                put("server", server.name)
                put("isError", result.isError == true)
                put("content", result.content.joinToString("\n") { content ->
                    (content as? TextContent)?.text ?: content.toString()
                })
                result.structuredContent?.let { put("structuredContent", it.toString()) }
            }
        }
    }

    private inner class McpRemoteTool(
        private val server: McpServer,
        private val remote: Tool,
        adkName: String,
    ) : FunctionTool(
        name = adkName,
        description = "MCP (${server.name}): ${remote.description ?: remote.name}",
        requiresConfirmation = true,
    ) {
        override fun declaration(): FunctionDeclaration = FunctionDeclaration(
            name = name,
            description = description,
            parameters = remote.inputSchema.toAdkSchema(),
        )

        override suspend fun execute(context: ToolContext, args: Map<String, Any>): Any =
            connect(server).use { it.call(remote, args) }
    }
}

internal fun selectMcpServers(
    servers: List<McpServer>,
    selectedServerIds: Set<String>?,
): List<McpServer> = if (selectedServerIds == null) {
    servers.filter(McpServer::isEnabled)
} else {
    servers.filter { it.id in selectedServerIds }
}

internal fun mcpToolName(serverId: String, remoteName: String): String? {
    if (!remoteName.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) return null
    val namespace = serverId.hashCode().toUInt().toString(16)
    val safeRemoteName = remoteName.replace('.', '_').take(40)
    return "mcp_${namespace}_$safeRemoteName".take(64)
}

private fun io.modelcontextprotocol.kotlin.sdk.types.ToolSchema.toAdkSchema(): Schema = Schema(
    type = com.google.adk.kt.types.Type.OBJECT,
    properties = properties?.mapValues { (_, value) -> (value as? JsonObject)?.toAdkSchema() ?: Schema() },
    required = required,
)

private fun JsonObject.toAdkSchema(): Schema {
    val type = (this["type"] as? JsonPrimitive)?.content
    return Schema(
        type = type?.toAdkType(),
        description = (this["description"] as? JsonPrimitive)?.content,
        properties = (this["properties"] as? JsonObject)?.mapValues { (_, value) ->
            (value as? JsonObject)?.toAdkSchema() ?: Schema()
        },
        items = (this["items"] as? JsonObject)?.toAdkSchema(),
        required = (this["required"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.content
        },
    )
}

private fun String.toAdkType(): com.google.adk.kt.types.Type? = when (this) {
    "object" -> com.google.adk.kt.types.Type.OBJECT
    "array" -> com.google.adk.kt.types.Type.ARRAY
    "string" -> com.google.adk.kt.types.Type.STRING
    "number" -> com.google.adk.kt.types.Type.NUMBER
    "integer" -> com.google.adk.kt.types.Type.INTEGER
    "boolean" -> com.google.adk.kt.types.Type.BOOLEAN
    else -> null
}
