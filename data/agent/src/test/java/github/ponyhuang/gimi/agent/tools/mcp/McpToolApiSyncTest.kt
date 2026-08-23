package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.tools.ToolContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.Tool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import reactor.core.publisher.Mono

class McpToolApiSyncTest {

    @Test
    fun declarationIsConvertedOnlyOnce() {
        val tool = mcpTool(mockk())

        val first = tool.declaration()
        val second = tool.declaration()

        assertSame(first, second)
    }

    @Test
    fun progressUsesTheAdkFunctionCallId() = runTest {
        val client = mockk<McpAsyncClient>()
        val request = slot<CallToolRequest>()
        every { client.callTool(capture(request)) } returns Mono.just(textResult("ok"))
        val tool = mcpTool(client, hasProgressConsumers = true)
        val context = mockk<ToolContext>()
        every { context.functionCallId } returns "function-call-1"

        tool.run(context, emptyMap())

        assertEquals("function-call-1", request.captured.meta()["progressToken"])
    }

    @Test
    fun emptySdkResultReturnsAnActionableErrorMap() = runTest {
        val client = mockk<McpAsyncClient>()
        every { client.callTool(any()) } returns Mono.empty()
        val tool = mcpTool(client)
        val context = mockk<ToolContext>()
        every { context.functionCallId } returns null

        val result = tool.run(context, emptyMap()) as Map<*, *>

        assertEquals("MCP framework error: CallToolResult was null", result["error"])
    }

    private fun mcpTool(
        client: McpAsyncClient,
        hasProgressConsumers: Boolean = false,
    ): McpTool =
        McpTool(
            name = "echo",
            description = "Echoes input.",
            mcpSchemaTool = Tool.builder("echo", mapOf("type" to "object")).build(),
            mcpSessionManager = StaticSessionManager(McpSession(client), hasProgressConsumers),
        )

    private fun textResult(text: String): CallToolResult =
        CallToolResult.builder().addTextContent(text).build()

    private class StaticSessionManager(
        private val session: McpSession,
        override val hasProgressConsumers: Boolean,
    ) : SessionManager {
        override suspend fun getSession(
            headers: Map<String, String>,
            stale: McpSession?,
        ): McpSession = session

        override fun close() = Unit
    }
}
