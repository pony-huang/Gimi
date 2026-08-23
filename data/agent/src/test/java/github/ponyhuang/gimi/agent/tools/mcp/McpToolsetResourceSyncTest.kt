package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.ToolContext
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult
import io.modelcontextprotocol.spec.McpSchema.Resource
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents
import io.modelcontextprotocol.spec.McpSchema.Tool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import reactor.core.publisher.Mono

class McpToolsetResourceSyncTest {

    @Test
    fun noArgListResourcesReturnsTheFirstTypedPage() = runTest {
        val client = mockk<McpAsyncClient>()
        every { client.listResources(null as String?) } returns
            Mono.just(
                ListResourcesResult.builder(
                    listOf(Resource.builder("file:///guide.md", "guide").build()),
                ).nextCursor("page-2").build(),
            )
        val toolset = McpToolset(StaticSessionManager(McpSession(client)))

        val listing: McpResourceListing = toolset.listResources()

        assertEquals("guide", listing.resources.single().name)
        assertEquals("page-2", listing.nextCursor)
    }

    @Test
    fun resourceToolsRemainEnabledWhenServerToolFilterRejectsEverything() = runTest {
        val client = resourceCapableClient()
        every { client.listTools() } returns Mono.just(
            ListToolsResult.builder(listOf(tool("echo"))).build(),
        )
        val toolset = McpToolset(
            mcpSessionManager = StaticSessionManager(McpSession(client)),
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
    fun listResourcesToolUsesCurrentSessionAndReturnsTheRequestedPage() = runTest {
        val staleClient = resourceCapableClient()
        every { staleClient.listTools() } returns Mono.just(ListToolsResult.builder(emptyList()).build())
        every { staleClient.listResources("cursor-1") } returns
            Mono.error(IllegalStateException("stale session"))

        val currentClient = resourceCapableClient()
        val resource =
            Resource.builder("file:///guide.md", "guide")
                .description("Project guide")
                .mimeType("text/markdown")
                .build()
        every { currentClient.listResources("cursor-1") } returns
            Mono.just(
                ListResourcesResult.builder(listOf(resource)).nextCursor("cursor-2").build(),
            )

        val manager = SequencedSessionManager(McpSession(staleClient), McpSession(currentClient))
        val toolset = McpToolset(manager, useMcpResources = true)
        val listTool = toolset.getTools(null).single { it.name == "list_mcp_resources" }

        @Suppress("UNCHECKED_CAST")
        val result = listTool.run(toolContext(), mapOf("cursor" to "cursor-1")) as Map<String, Any>

        assertEquals("cursor-2", result["nextCursor"])
        val resources = result["resources"] as List<*>
        assertEquals(
            mapOf(
                "name" to "guide",
                "uri" to "file:///guide.md",
                "description" to "Project guide",
                "mimeType" to "text/markdown",
            ),
            resources.single(),
        )
    }

    @Test
    fun loadResourceByUniqueNameFollowsAllPagesAndReadsResolvedUri() = runTest {
        val client = mockk<McpAsyncClient>()
        every { client.listResources(null as String?) } returns
            Mono.just(
                ListResourcesResult.builder(
                    listOf(Resource.builder("file:///other.txt", "other").build()),
                ).nextCursor("page-2").build(),
            )
        every { client.listResources("page-2") } returns
            Mono.just(
                ListResourcesResult.builder(
                    listOf(Resource.builder("file:///guide.md", "guide").build()),
                ).build(),
            )
        every { client.readResource(match<ReadResourceRequest> { it.uri() == "file:///guide.md" }) } returns
            Mono.just(
                ReadResourceResult.builder(
                    listOf(TextResourceContents.builder("file:///guide.md", "hello").build()),
                ).build(),
            )
        val toolset = McpToolset(StaticSessionManager(McpSession(client)))
        val loadTool = LoadMcpResourceTool(toolset, maxMcpResourceLength = 100)

        val result = loadTool.run(toolContext(), mapOf("name" to "guide"))

        assertEquals("hello", result)
    }

    @Test
    fun loadResourceReportsConflictingArgumentsWithoutCallingTheServer() = runTest {
        val client = mockk<McpAsyncClient>()
        val toolset = McpToolset(StaticSessionManager(McpSession(client)))
        val loadTool = LoadMcpResourceTool(toolset, maxMcpResourceLength = 100)

        val result =
            loadTool.run(
                toolContext(),
                mapOf("name" to "guide", "uri" to "file:///guide.md"),
            )

        assertTrue(result.toString().contains("exactly one"))
    }

    @Test
    fun resourceContentKeepsTextAndRepresentsBinaryWithoutLeakingSdkTypes() = runTest {
        val client = mockk<McpAsyncClient>()
        every { client.readResource(match<ReadResourceRequest> { it.uri() == "file:///mixed" }) } returns
            Mono.just(
                ReadResourceResult.builder(
                    listOf(
                        TextResourceContents.builder("file:///mixed", "hello").build(),
                        BlobResourceContents.builder("file:///mixed", "AQID").build(),
                    ),
                ).build(),
            )
        val loadTool =
            LoadMcpResourceTool(
                McpToolset(StaticSessionManager(McpSession(client))),
                maxMcpResourceLength = 100,
            )

        val result = loadTool.run(toolContext(), mapOf("uri" to "file:///mixed"))

        assertEquals(
            "hello\n\n[Warning: Binary data found at this URI, cannot display raw content]",
            result,
        )
    }

    private fun resourceCapableClient(): McpAsyncClient = mockk<McpAsyncClient>().also { client ->
        every { client.serverCapabilities } returns
            ServerCapabilities.builder().tools(false).resources(false, false).build()
    }

    private fun tool(name: String): Tool =
        Tool.builder(name, mapOf("type" to "object")).description("Test tool").build()

    private fun toolContext(): ToolContext = mockk<ToolContext>().also { context ->
        every { context.context } returns mockk<ReadonlyContext>()
    }

    private class StaticSessionManager(private val session: McpSession) : SessionManager {
        override suspend fun getSession(
            headers: Map<String, String>,
            stale: McpSession?,
        ): McpSession = session

        override fun close() = Unit

        override val hasProgressConsumers: Boolean = false
    }

    private class SequencedSessionManager(vararg sessions: McpSession) : SessionManager {
        private val sessions = sessions.toList()
        private var index = 0

        override suspend fun getSession(
            headers: Map<String, String>,
            stale: McpSession?,
        ): McpSession = sessions[index.coerceAtMost(sessions.lastIndex)].also { index++ }

        override fun close() = Unit

        override val hasProgressConsumers: Boolean = false
    }
}
