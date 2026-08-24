package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.ToolContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolsetResourceSyncTest {

    @Test
    fun noArgListResourcesReturnsTheFirstTypedPage() = runTest {
        val client = mockk<Client>()
        coEvery { client.listResources(any(), any()) } returns
            ListResourcesResult(
                resources = listOf(Resource(uri = "file:///guide.md", name = "guide")),
                nextCursor = "page-2",
            )
        val toolset = McpToolset(StaticSessionManager(session(client)))

        val listing = toolset.listResources()

        assertEquals("guide", listing.resources.single().name)
        assertEquals("page-2", listing.nextCursor)
    }

    @Test
    fun resourceToolsRemainEnabledWhenServerToolFilterRejectsEverything() = runTest {
        val client = resourceCapableClient()
        coEvery { client.listTools(any(), any()) } returns ListToolsResult(listOf(tool("echo")))
        val toolset =
            McpToolset(
                mcpSessionManager = StaticSessionManager(session(client)),
                toolFilter = ToolFilter.allowList("missing"),
                useMcpResources = true,
            )

        val names = toolset.getTools(null).map { it.name }

        assertEquals(
            listOf("list_mcp_resources", "load_mcp_resource", "list_mcp_resource_templates"),
            names,
        )
    }

    @Test
    fun listResourcesToolReplacesOnlyTheFailedSession() = runTest {
        val staleClient = resourceCapableClient()
        coEvery { staleClient.listTools(any(), any()) } returns ListToolsResult(emptyList())
        coEvery { staleClient.listResources(any(), any()) } throws IllegalStateException("stale")
        val currentClient = resourceCapableClient()
        coEvery { currentClient.listResources(any(), any()) } returns
            ListResourcesResult(
                resources =
                    listOf(
                        Resource(
                            uri = "file:///guide.md",
                            name = "guide",
                            description = "Project guide",
                            mimeType = "text/markdown",
                        ),
                    ),
                nextCursor = "cursor-2",
            )
        val manager = SequencedSessionManager(session(staleClient), session(currentClient))
        val toolset = McpToolset(manager, useMcpResources = true)
        val listTool = toolset.getTools(null).single { it.name == "list_mcp_resources" }

        @Suppress("UNCHECKED_CAST")
        val result = listTool.run(toolContext(), mapOf("cursor" to "cursor-1")) as Map<String, Any>

        assertEquals("cursor-2", result["nextCursor"])
        assertEquals("guide", ((result["resources"] as List<*>).single() as Map<*, *>)["name"])
    }

    @Test
    fun loadResourceByUniqueNameFollowsAllPagesAndReadsResolvedUri() = runTest {
        val client = mockk<Client>()
        var page = 0
        coEvery { client.listResources(any(), any()) } answers {
            if (page++ == 0) {
                ListResourcesResult(
                    listOf(Resource(uri = "file:///other.txt", name = "other")),
                    "page-2",
                )
            } else {
                ListResourcesResult(listOf(Resource(uri = "file:///guide.md", name = "guide")))
            }
        }
        coEvery { client.readResource(match<ReadResourceRequest> { it.uri == "file:///guide.md" }, any()) } returns
            ReadResourceResult(listOf(TextResourceContents(text = "hello", uri = "file:///guide.md")))
        val loadTool =
            LoadMcpResourceTool(
                McpToolset(StaticSessionManager(session(client))),
                maxMcpResourceLength = 100,
            )

        val result = loadTool.run(toolContext(), mapOf("name" to "guide"))

        assertEquals("hello", result)
    }

    @Test
    fun loadResourceReportsConflictingArgumentsWithoutCallingTheServer() = runTest {
        val loadTool =
            LoadMcpResourceTool(
                McpToolset(StaticSessionManager(session(mockk()))),
                maxMcpResourceLength = 100,
            )

        val result =
            loadTool.run(
                toolContext(),
                mapOf("name" to "guide", "uri" to "file:///guide.md"),
            )

        assertTrue(result.toString().contains("exactly one"))
    }

    @Test
    fun resourceContentKeepsTextAndRepresentsBinaryWithoutLeakingSdkTypes() = runTest {
        val client = mockk<Client>()
        coEvery { client.readResource(match<ReadResourceRequest> { it.uri == "file:///mixed" }, any()) } returns
            ReadResourceResult(
                listOf(
                    TextResourceContents(text = "hello", uri = "file:///mixed"),
                    BlobResourceContents(blob = "AQID", uri = "file:///mixed"),
                ),
            )
        val loadTool =
            LoadMcpResourceTool(
                McpToolset(StaticSessionManager(session(client))),
                maxMcpResourceLength = 100,
            )

        val result = loadTool.run(toolContext(), mapOf("uri" to "file:///mixed"))

        assertEquals(
            "hello\n\n[Warning: Binary data found at this URI, cannot display raw content]",
            result,
        )
    }

    private fun resourceCapableClient(): Client = mockk<Client>().also { client ->
        every { client.serverCapabilities } returns
            ServerCapabilities(tools = ServerCapabilities.Tools(), resources = ServerCapabilities.Resources())
    }

    private fun tool(name: String): Tool =
        Tool(name = name, inputSchema = ToolSchema(), description = "Test tool")

    private fun session(client: Client): McpSession =
        McpSession(client, McpTransportHandle(NoOpTransport()))

    private fun toolContext(): ToolContext = mockk<ToolContext>().also { context ->
        every { context.context } returns mockk<ReadonlyContext>()
    }

    private class StaticSessionManager(private val session: McpSession) : SessionManager {
        override suspend fun getSession(headers: Map<String, String>, stale: McpSession?): McpSession = session
        override fun requestOptions(): RequestOptions = RequestOptions()
        override fun close() = Unit
        override val hasProgressConsumers: Boolean = false
    }

    private class SequencedSessionManager(vararg sessions: McpSession) : SessionManager {
        private val sessions = sessions.toList()
        private var index = 0

        override suspend fun getSession(headers: Map<String, String>, stale: McpSession?): McpSession =
            sessions[index.coerceAtMost(sessions.lastIndex)].also { index++ }

        override fun requestOptions(): RequestOptions = RequestOptions()
        override fun close() = Unit
        override val hasProgressConsumers: Boolean = false
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
