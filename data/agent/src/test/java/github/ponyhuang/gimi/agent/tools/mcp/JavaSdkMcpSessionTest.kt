package github.ponyhuang.gimi.agent.tools.mcp

import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.json.TypeRef
import io.modelcontextprotocol.spec.McpClientTransport
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import reactor.core.publisher.Mono

class JavaSdkMcpSessionTest {
  @Test
  fun `initializes and lists tools through Java SDK transport contract`() = runBlocking {
    val transport = InMemoryTransport()
    val builder = object : McpTransportBuilder {
      override fun build(connectionParams: McpConnectionParameters): McpClientTransport = transport
    }
    val session =
      McpSessionManager.createSession(
        McpConnectionParameters.StreamableHttp("https://example.test/mcp", readTimeout = 1.seconds),
        builder,
      )

    session.connect()
    val tools = session.client.listTools().awaitSingle().tools()
    session.close()

    assertEquals(listOf("echo"), tools.map { it.name() })
    assertTrue(transport.closed.get())
  }

  private class InMemoryTransport : McpClientTransport {
    private val mapper = McpJsonDefaults.getMapper()
    private lateinit var handler: Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>>
    val closed = AtomicBoolean(false)

    override fun connect(
      handler: Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>>,
    ): Mono<Void> = Mono.fromRunnable { this.handler = handler }

    override fun sendMessage(message: JSONRPCMessage): Mono<Void> {
      if (message is JSONRPCNotification) return Mono.empty()
      require(message is JSONRPCRequest)
      val result =
        when (message.method()) {
          "initialize" ->
            mapOf(
              "protocolVersion" to "2025-11-25",
              "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
              "serverInfo" to mapOf("name" to "test", "version" to "1"),
            )
          "tools/list" ->
            mapOf(
              "tools" to
                listOf(
                  mapOf(
                    "name" to "echo",
                    "description" to "Echoes input.",
                    "inputSchema" to mapOf("type" to "object"),
                  )
                )
            )
          else -> error("Unexpected MCP method ${message.method()}")
        }
      return handler.apply(Mono.just(JSONRPCResponse.result(message.id(), result))).then()
    }

    override fun closeGracefully(): Mono<Void> = Mono.fromRunnable { closed.set(true) }

    override fun <T : Any?> unmarshalFrom(data: Any, typeRef: TypeRef<T>): T =
      mapper.convertValue(data, typeRef)
  }
}
