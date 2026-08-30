package github.ponyhuang.gimi.data.agent.tools.mcp

import com.google.adk.kt.tools.ToolContext
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class McpToolApiSyncTest {

    @Test
    fun declarationIsConvertedOnlyOnce() {
        val tool = mcpTool(mockk())

        val first = tool.declaration()
        val second = tool.declaration()

        assertSame(first, second)
    }

    @Test
    fun progressConsumerIsForwardedThroughKotlinRequestOptions() = runTest {
        val client = mockk<Client>()
        val options = slot<RequestOptions>()
        coEvery {
            client.callTool(name = "echo", arguments = any(), options = capture(options))
        } returns textResult("ok")
        val tool = mcpTool(client, hasProgressConsumers = true)

        tool.run(mockk<ToolContext>(), emptyMap())

        assertNotNull(options.captured.onProgress)
    }

    @Test
    fun sdkResultIsConvertedToJsonNativeContent() = runTest {
        val client = mockk<Client>()
        coEvery {
            client.callTool(name = "echo", arguments = any(), options = any())
        } returns textResult("ok")
        val tool = mcpTool(client)

        val result = tool.run(mockk<ToolContext>(), emptyMap()) as Map<*, *>
        val content = result["content"] as List<*>

        assertEquals("ok", (content.single() as Map<*, *>)["text"])
    }

    private fun mcpTool(
        client: Client,
        hasProgressConsumers: Boolean = false,
    ): McpTool =
        McpTool(
            name = "echo",
            description = "Echoes input.",
            mcpSchemaTool = Tool(name = "echo", inputSchema = ToolSchema()),
            mcpSessionManager =
                StaticSessionManager(
                    McpSession(client, McpTransportHandle(NoOpTransport())),
                    hasProgressConsumers,
                ),
        )

    private fun textResult(text: String): CallToolResult =
        CallToolResult(content = listOf(TextContent(text)))

    private class StaticSessionManager(
        private val session: McpSession,
        override val hasProgressConsumers: Boolean,
    ) : SessionManager {
        override suspend fun getSession(
            headers: Map<String, String>,
            stale: McpSession?,
        ): McpSession = session

        override fun requestOptions(): RequestOptions =
            RequestOptions(onProgress = if (hasProgressConsumers) ({}) else null)

        override fun close() = Unit
    }

    private class NoOpTransport : Transport {
        override suspend fun start() = Unit
        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) = Unit
        override suspend fun close() = Unit
        override fun onClose(block: () -> Unit) = Unit
        override fun onError(block: (Throwable) -> Unit) = Unit
        override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) = Unit
    }
}
