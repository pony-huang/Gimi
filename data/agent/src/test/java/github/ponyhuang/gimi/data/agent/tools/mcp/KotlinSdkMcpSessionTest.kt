package github.ponyhuang.gimi.data.agent.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinSdkMcpSessionTest {
    @Test
    fun `initializes and lists tools through Kotlin SDK transport contract`() = runBlocking {
        val transport = InMemoryTransport()
        val builder = object : McpTransportBuilder {
            override fun build(connectionParams: McpConnectionParameters): McpTransportHandle =
                McpTransportHandle(transport) { transport.released.set(true) }
        }
        val session =
            McpSessionManager.createSession(
                McpConnectionParameters.StreamableHttp(
                    "https://example.test/mcp",
                    readTimeout = 1.seconds,
                ),
                builder,
            )

        session.connect()
        val tools = session.client.listTools().tools
        session.close()

        assertEquals(listOf("echo"), tools.map { it.name })
        assertTrue(transport.closed.get())
        assertTrue(transport.released.get())
    }

    private class InMemoryTransport : Transport {
        private lateinit var messageHandler: suspend (JSONRPCMessage) -> Unit
        private var closeHandler: () -> Unit = {}
        val closed = AtomicBoolean(false)
        val released = AtomicBoolean(false)

        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
            if (message is JSONRPCNotification) return
            require(message is JSONRPCRequest)
            val result =
                when (message.method) {
                    "initialize" ->
                        InitializeResult(
                            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools()),
                            serverInfo = Implementation(name = "test", version = "1"),
                        )
                    "tools/list" ->
                        ListToolsResult(
                            tools =
                                listOf(
                                    Tool(
                                        name = "echo",
                                        description = "Echoes input.",
                                        inputSchema = ToolSchema(),
                                    ),
                                ),
                        )
                    else -> error("Unexpected MCP method ${message.method}")
                }
            messageHandler(JSONRPCResponse(message.id, result))
        }

        override suspend fun close() {
            closed.set(true)
            closeHandler()
        }

        override fun onClose(block: () -> Unit) {
            closeHandler = block
        }

        override fun onError(block: (Throwable) -> Unit) = Unit

        override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
            messageHandler = block
        }
    }
}
