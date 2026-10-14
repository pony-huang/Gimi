package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

/** 本地协议服务验证官方工具发现、调用、资源与设置页元数据，无真实网络或凭据。 */
class OfficialMcpIntegrationTest {
    @Test
    fun optionalResourceFailureDoesNotInvalidateToolHandshake() = runBlocking {
        val server = server(CopyOnWriteArrayList(), resourcesFail = true)
        try {
            val result = AdkMcpConnectionTester().test(McpServer(endpointUrl = server.url("/mcp").toString()))
            assertTrue(result.reachable)
            assertTrue(result.tools.any { it.name == "echo" })
            assertTrue(result.resources.isEmpty())
        } finally { server.shutdown() }
    }
    @Test
    fun officialToolsetDiscoversCallsAndSkipsReservedTools() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        val server = server(requests)
        val toolset = McpServer(endpointUrl = server.url("/mcp").toString()).toMcpToolset()
        try {
            val tools = toolset.getTools(null)
            assertTrue(tools.any { it.name == "echo" })
            assertFalse(tools.any { it.name == "transfer_to_agent" })
            assertTrue(tools.any { it.name == "list_mcp_resources" })
            val result = tools.single { it.name == "echo" }.run(mockk<ToolContext>(relaxed = true), mapOf("message" to "hello"))
            assertTrue(result.toString().contains("hello"))
            assertEquals(1, requests.count { it == "tools/call" })
        } finally {
            toolset.close()
            server.shutdown()
        }
    }

    @Test
    fun probeKeepsRawInputSchemaAndResourceNames() = runBlocking {
        val server = server(CopyOnWriteArrayList())
        try {
            val result = AdkMcpConnectionTester().test(McpServer(endpointUrl = server.url("/mcp").toString()))
            assertTrue(result.errorMessage.orEmpty(), result.reachable)
            assertEquals(listOf("guide"), result.resources)
            val schema = result.tools.single { it.name == "echo" }.inputSchema!!
            assertEquals("fallback", schema["properties"]!!.jsonObject["message"]!!.jsonObject["default"]!!.jsonPrimitive.content)
        } finally { server.shutdown() }
    }

    private fun server(requests: MutableList<String>, resourcesFail: Boolean = false) = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method != "POST") return MockResponse().setResponseCode(if (request.method == "DELETE") 204 else 405)
                val message = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                val method = message["method"]!!.jsonPrimitive.content
                requests += method
                val id = message["id"] ?: return MockResponse().setResponseCode(202)
                if (resourcesFail && method == "resources/list") {
                    return MockResponse().setHeader("Content-Type", "application/json")
                        .setBody("""{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"Unsupported resource method"}}""")
                }
                val result = when (method) {
                    "initialize" -> """{"protocolVersion":"2025-06-18","capabilities":{"tools":{},"resources":{}},"serverInfo":{"name":"test","version":"1"}}"""
                    "tools/list" -> """{"tools":[{"name":"echo","description":"Echo","inputSchema":{"type":"object","properties":{"message":{"type":"string","default":"fallback"}}}},{"name":"transfer_to_agent","inputSchema":{"type":"object"}}]}"""
                    "tools/call" -> """{"content":[{"type":"text","text":"hello"}],"isError":false}"""
                    "resources/list" -> """{"resources":[{"name":"guide","uri":"test://guide"}]}"""
                    else -> "{}"
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"jsonrpc":"2.0","id":$id,"result":$result}""")
            }
        }
        start()
    }
}
