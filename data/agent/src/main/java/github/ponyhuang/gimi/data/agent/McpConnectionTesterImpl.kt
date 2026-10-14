package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.tools.mcp.McpTransportConfig
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpToolSummary
import github.ponyhuang.gimi.domain.mcp.repository.McpConnectionTester
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.encodedPath
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.seconds

/** 设置页元数据探测独立使用官方 SDK；不构造自有 ADK 工具，也不污染聊天连接池。 */
@Singleton
class AdkMcpConnectionTester @Inject constructor() : McpConnectionTester {
    override suspend fun test(server: McpServer): McpProbeResult {
        val http = HttpClient(OkHttp) {
            install(SSE)
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }
        val client = Client(Implementation("gimi-probe", "1.0"))
        try {
            return withTimeout(60.seconds) {
                val config = server.toOfficialMcpConfig().transport as McpTransportConfig.StreamableHttp
                val endpoint = io.ktor.http.URLBuilder(config.url).apply {
                    if (encodedPath.isBlank() || encodedPath == "/") encodedPath = "/mcp"
                }.buildString()
                client.connect(StreamableHttpClientTransport(http, endpoint) {
                    config.headers.forEach { (name, value) -> headers.append(name, value) }
                })
                val tools = mutableListOf<McpToolSummary>()
                var cursor: String? = null
                do {
                    val page = client.listTools(ListToolsRequest(PaginatedRequestParams(cursor)))
                    tools += page.tools.map { tool ->
                        McpToolSummary(
                            name = tool.name,
                            description = tool.description.orEmpty(),
                            inputSchema = McpJson.encodeToJsonElement(ToolSchema.serializer(), tool.inputSchema) as JsonObject,
                        )
                    }
                    cursor = page.nextCursor
                } while (cursor != null)
                val resources = mutableListOf<String>()
                if (client.serverCapabilities?.resources != null) {
                    try {
                        do {
                            val page = client.listResources(ListResourcesRequest(PaginatedRequestParams(cursor)))
                            resources += page.resources.map { it.name }
                            cursor = page.nextCursor
                        } while (cursor != null)
                    } catch (_: McpException) {
                        // 可选资源接口拒绝不应把已经完成的工具握手判为失败。
                    }
                }
                McpProbeResult(reachable = true, tools = tools, resources = resources)
            }
        } catch (_: TimeoutCancellationException) {
            return McpProbeResult(reachable = false, errorMessage = "mcp.connection_error")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // SDK 错误可能包含请求头或服务端正文，不向 UI 输出原始异常。
            return McpProbeResult(reachable = false, errorMessage = "mcp.connection_error")
        } finally {
            withContext(NonCancellable) {
                try { withTimeout(5.seconds) { client.close() } } finally { http.close() }
            }
        }
    }
}
