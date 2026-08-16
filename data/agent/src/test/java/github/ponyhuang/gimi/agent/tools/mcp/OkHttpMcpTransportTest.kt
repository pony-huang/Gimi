package github.ponyhuang.gimi.agent.tools.mcp

import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.spec.HttpHeaders
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest
import io.modelcontextprotocol.spec.ProtocolVersions
import java.time.Duration
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class OkHttpMcpTransportTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `legacy SSE appends endpoint to a base path without trailing slash`() {
    server.enqueue(
      MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody("event: endpoint\ndata: /messages\n\n")
    )
    server.enqueue(MockResponse().setResponseCode(202))
    val transport =
      OkHttpSseClientTransport(
        baseUrl = server.url("/api").toString(),
        sseEndpoint = "sse",
        headers = mapOf("Authorization" to "Bearer test"),
        client = OkHttpClient(),
        jsonMapper = McpJsonDefaults.getMapper(),
      )

    transport.connect { it }.block(Duration.ofSeconds(5))
    transport.sendMessage(JSONRPCRequest("ping", 1, emptyMap<String, Any>())).block(Duration.ofSeconds(5))

    val get = server.takeRequest(5, TimeUnit.SECONDS)
    val post = server.takeRequest(5, TimeUnit.SECONDS)
    assertEquals("/api/sse", get?.path)
    assertEquals("Bearer test", get?.getHeader("Authorization"))
    assertEquals(ProtocolVersions.MCP_2024_11_05, get?.getHeader(HttpHeaders.PROTOCOL_VERSION))
    assertEquals("/messages", post?.path)
    transport.closeGracefully().block(Duration.ofSeconds(5))
  }

  @Test
  fun `streamable HTTP retains session and dispatches JSON response`() {
    server.enqueue(
      MockResponse()
        .setHeader("Content-Type", "application/json")
        .setHeader(HttpHeaders.MCP_SESSION_ID, "session-1")
        .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}")
    )
    server.enqueue(MockResponse().setResponseCode(405))
    server.enqueue(MockResponse().setResponseCode(200))
    val transport =
      OkHttpStreamableHttpTransport(
        url = server.url("/mcp").toString(),
        headers = mapOf("X-Test" to "yes"),
        client = OkHttpClient(),
        jsonMapper = McpJsonDefaults.getMapper(),
      )
    var received: Any? = null
    transport.connect { incoming -> incoming.doOnNext { received = it } }.block(Duration.ofSeconds(5))

    transport.sendMessage(JSONRPCRequest("ping", 1, emptyMap<String, Any>())).block(Duration.ofSeconds(5))

    val post = server.takeRequest(5, TimeUnit.SECONDS)
    val listener = server.takeRequest(5, TimeUnit.SECONDS)
    assertEquals("POST", post?.method)
    assertEquals("yes", post?.getHeader("X-Test"))
    assertEquals("GET", listener?.method)
    assertEquals("session-1", listener?.getHeader(HttpHeaders.MCP_SESSION_ID))
    assertNotNull(received)

    transport.closeGracefully().block(Duration.ofSeconds(5))
    val delete = server.takeRequest(5, TimeUnit.SECONDS)
    assertEquals("DELETE", delete?.method)
    assertEquals("session-1", delete?.getHeader(HttpHeaders.MCP_SESSION_ID))
    assertEquals(ProtocolVersions.MCP_2025_11_25, delete?.getHeader(HttpHeaders.PROTOCOL_VERSION))
  }
}
