package github.ponyhuang.gimi.data.agent.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

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
  fun `legacy SSE appends endpoint to a base path without trailing slash`() = runBlocking {
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
      )

    transport.onMessage {}
    transport.start()
    transport.send(JSONRPCRequest(id = 1L, method = "ping"))

    val get = server.takeRequest(5, TimeUnit.SECONDS)
    val post = server.takeRequest(5, TimeUnit.SECONDS)
    assertEquals("/api/sse", get?.path)
    assertEquals("Bearer test", get?.getHeader("Authorization"))
    assertEquals("2024-11-05", get?.getHeader("MCP-Protocol-Version"))
    assertEquals("/messages", post?.path)
    transport.close()
  }

  @Test
  fun `streamable HTTP retains session and dispatches JSON response`() = runBlocking {
    server.enqueue(
      MockResponse()
        .setHeader("Content-Type", "application/json")
        .setHeader("Mcp-Session-Id", "session-1")
        .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}")
    )
    server.enqueue(MockResponse().setResponseCode(405))
    server.enqueue(MockResponse().setResponseCode(200))
    val transport =
      OkHttpStreamableHttpTransport(
        url = server.url("/mcp").toString(),
        headers = mapOf("X-Test" to "yes"),
        client = OkHttpClient(),
      )
    var received: Any? = null
    transport.onMessage { received = it }
    transport.start()

    transport.send(JSONRPCRequest(id = 1L, method = "ping"))

    val post = server.takeRequest(5, TimeUnit.SECONDS)
    val listener = server.takeRequest(5, TimeUnit.SECONDS)
    assertEquals("POST", post?.method)
    assertEquals("yes", post?.getHeader("X-Test"))
    assertEquals("GET", listener?.method)
    assertEquals("session-1", listener?.getHeader("Mcp-Session-Id"))
    assertNotNull(received)

    transport.close()
    val delete = server.takeRequest(5, TimeUnit.SECONDS)
    assertEquals("DELETE", delete?.method)
    assertEquals("session-1", delete?.getHeader("Mcp-Session-Id"))
    assertEquals("2025-11-25", delete?.getHeader("MCP-Protocol-Version"))
  }
}
