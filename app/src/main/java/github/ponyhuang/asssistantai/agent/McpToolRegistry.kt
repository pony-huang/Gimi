package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import github.ponyhuang.asssistantai.domain.mcp.repository.McpRepository
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

/** Discovers remote MCP tools and exposes them as regular ADK tools. */
@Singleton
class McpToolRegistry @Inject constructor(
    private val servers: McpRepository,
) {
    suspend fun tools(): List<BaseTool> = servers.currentServers()
        .filter { it.isEnabled && it.endpointUrl.isNotBlank() }
        .flatMap { server -> runCatching { discover(server) }.getOrElse { emptyList() } }

    private suspend fun discover(server: McpServer): List<BaseTool> {
        val connection = connect(server)
        return try {
            connection.client.listTools().tools.map { tool -> McpRemoteTool(server, tool) }
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
        client.connect(transport)
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
    ) : FunctionTool(
        name = "mcp_${server.id.take(8)}_${remote.name}".replace(Regex("[^A-Za-z0-9_-]"), "_"),
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
