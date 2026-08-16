package github.ponyhuang.gimi.agent.tools.mcp

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.json.TypeRef
import io.modelcontextprotocol.spec.McpClientTransport
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities
import io.modelcontextprotocol.spec.McpSchema.Implementation
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import reactor.core.publisher.Mono

@RunWith(AndroidJUnit4::class)
class JavaSdkAndroidRuntimeTest {
  @Test
  fun initializesAndClosesJavaSdkClientOnAndroidRuntime() {
    val transport = InMemoryTransport()
    val client =
      McpClient.async(transport)
        .clientInfo(Implementation.builder("android-test", "1").build())
        .capabilities(ClientCapabilities.builder().build())
        .requestTimeout(Duration.ofSeconds(2))
        .initializationTimeout(Duration.ofSeconds(2))
        .build()

    val initialized = client.initialize().block(Duration.ofSeconds(2))
    client.closeGracefully().block(Duration.ofSeconds(2))

    assertEquals("android-test-server", initialized?.serverInfo()?.name())
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
      require(message is JSONRPCRequest && message.method() == "initialize")
      val result =
        mapOf(
          "protocolVersion" to "2025-11-25",
          "capabilities" to emptyMap<String, Any>(),
          "serverInfo" to mapOf("name" to "android-test-server", "version" to "1"),
        )
      return handler.apply(Mono.just(JSONRPCResponse.result(message.id(), result))).then()
    }

    override fun closeGracefully(): Mono<Void> = Mono.fromRunnable { closed.set(true) }

    override fun <T : Any?> unmarshalFrom(data: Any, typeRef: TypeRef<T>): T =
      mapper.convertValue(data, typeRef)
  }
}
