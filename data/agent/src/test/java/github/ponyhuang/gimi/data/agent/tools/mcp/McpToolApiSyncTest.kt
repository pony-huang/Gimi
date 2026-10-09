package github.ponyhuang.gimi.data.agent.tools.mcp

import com.google.adk.kt.tools.ToolContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val callbackOptions = RequestOptions(onProgress = {})
        val tool = mcpTool(client, hasProgressConsumers = true, optionsOverride = callbackOptions)

        tool.run(mockk<ToolContext>(), emptyMap())

        assertNotNull(options.captured.onProgress)
        assertSame(callbackOptions, options.captured)
    }

    @Test
    fun sdkResultIsConvertedToJsonNativeContent() = runTest {
        val client = mockk<Client>()
        coEvery {
            client.callTool(name = "echo", arguments = any(), options = any())
        } returns textResult("ok")
        val tool = mcpTool(client)
        val arguments = mapOf("text" to "hello", "repeat" to 2, "enabled" to true)

        val result = tool.run(mockk<ToolContext>(), arguments) as Map<*, *>
        val content = result["content"] as List<*>

        assertEquals("ok", (content.single() as Map<*, *>)["text"])
        assertEquals("text", (content.single() as Map<*, *>)["type"])
        coVerify(exactly = 1) { client.callTool(name = "echo", arguments = arguments, options = any()) }
    }

    /**
     * MCP server 返回 JSON-RPC 错误响应（如"余额不足"）时 SDK 会抛 McpException；
     * McpTool 必须把它兜成结构化错误 Map 让 ADK 写入 tool_result —— 否则下一轮
     * Anthropic 会以 `tool_use ids were found without tool_result blocks` 拒绝整次请求。
     */
    @Test
    fun serverError_isReturnedAsStructuredErrorMap() = runTest {
        val client = mockk<Client>()
        coEvery {
            client.callTool(name = "echo", arguments = any(), options = any())
        } throws McpException(
            code = -32001,
            message = "tool调用失败，原因：余额不足",
        )
        val tool = mcpTool(client)

        val result = tool.run(mockk<ToolContext>(), mapOf("text" to "hi")) as Map<*, *>

        assertNotNull(result["error"])
        assertEquals("tool调用失败，原因：余额不足", result["error"])
        assertFalse("error map should not leak SDK content shape", result.containsKey("content"))
    }

    /** server-side 决策（final），retry 无意义；McpException 必须短路、不重试。 */
    @Test
    fun serverError_doesNotRetry() = runTest {
        val client = mockk<Client>()
        coEvery {
            client.callTool(name = "echo", arguments = any(), options = any())
        } throws McpException(code = -32001, message = "quota exhausted")
        val tool = mcpTool(client)

        tool.run(mockk<ToolContext>(), emptyMap())

        coVerify(exactly = 1) { client.callTool(name = "echo", arguments = any(), options = any()) }
    }

    /** transport 级异常（非 McpException）仍按原 4 次重试，最后兜成 error Map。 */
    @Test
    fun transportError_stillRetriesThenSurfacesAsErrorMap() = runTest {
        val client = mockk<Client>()
        coEvery {
            client.callTool(name = "echo", arguments = any(), options = any())
        } throws IOException("connection reset")
        val tool = mcpTool(client)

        val result = tool.run(mockk<ToolContext>(), emptyMap()) as Map<*, *>

        // retrySessionCall 的 times=4（3 次循环 + 1 次 final），transport 错仍走满
        coVerify(exactly = 4) { client.callTool(name = "echo", arguments = any(), options = any()) }
        assertEquals("connection reset", result["error"])
    }

    private fun mcpTool(
        client: Client,
        hasProgressConsumers: Boolean = false,
        optionsOverride: RequestOptions? = null,
    ): McpTool =
        McpTool(
            name = "echo",
            description = "Echoes input.",
            mcpSchemaTool = Tool(name = "echo", inputSchema = ToolSchema()),
            mcpSessionManager =
                StaticSessionManager(
                    McpSession(client, McpTransportHandle(NoOpTransport())),
                    hasProgressConsumers,
                    optionsOverride,
                ),
        )

    private fun textResult(text: String): CallToolResult =
        CallToolResult(content = listOf(TextContent(text)))

    private class StaticSessionManager(
        private val session: McpSession,
        override val hasProgressConsumers: Boolean,
        private val optionsOverride: RequestOptions?,
    ) : SessionManager {
        override suspend fun getSession(
            headers: Map<String, String>,
            stale: McpSession?,
        ): McpSession = session

        override fun requestOptions(): RequestOptions =
            optionsOverride ?: RequestOptions(onProgress = if (hasProgressConsumers) ({}) else null)

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
