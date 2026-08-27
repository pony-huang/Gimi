package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.domain.mcp.model.McpManualConfigurationResult
import github.ponyhuang.gimi.domain.mcp.model.McpTransport
import github.ponyhuang.gimi.domain.mcp.usecase.ConfigureMcpServerForConversationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpManualConfigurationToolTest {
    @Test
    fun configuresServerForCurrentSessionAndReturnsOnlySafeSummary() = runTest {
        val useCase = mockk<ConfigureMcpServerForConversationUseCase>()
        coEvery {
            useCase(
                sessionId = "session-1",
                name = "maps",
                endpointUrl = "https://secret.example.com/mcp",
                transport = McpTransport.SSE,
                description = "Maps server",
                bearerToken = "secret-token",
                headers = "X-Client=assistant",
                enabled = true,
            )
        } returns McpManualConfigurationResult(
            serverId = "mcp-d",
            serverName = "maps",
            created = true,
            updated = false,
            conversationActivated = true,
        )
        val tool = McpManualConfigurationTool(useCase)
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", "session-1"),
            )
        }

        val response = tool.run(
            context,
            mapOf(
                "name" to "maps",
                "endpoint_url" to "https://secret.example.com/mcp",
                "transport" to "sse",
                "description" to "Maps server",
                "bearer_token" to "secret-token",
                "headers" to "X-Client=assistant",
                "enabled" to true,
            ),
        ) as Map<*, *>

        coVerify(exactly = 1) {
            useCase(
                sessionId = "session-1",
                name = "maps",
                endpointUrl = "https://secret.example.com/mcp",
                transport = McpTransport.SSE,
                description = "Maps server",
                bearerToken = "secret-token",
                headers = "X-Client=assistant",
                enabled = true,
            )
        }
        assertEquals(true, response["success"])
        assertEquals(true, response["created"])
        assertEquals("maps", response["server"])
        assertFalse(response.toString().contains("secret.example.com"))
        assertFalse(response.toString().contains("secret-token"))
    }

    @Test
    fun transportDefaultsToStreamableHttpWhenAbsent() = runTest {
        val useCase = mockk<ConfigureMcpServerForConversationUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            McpManualConfigurationResult(
                serverId = "mcp-d",
                serverName = "maps",
                created = false,
                updated = true,
                conversationActivated = true,
            )
        val tool = McpManualConfigurationTool(useCase)
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", "session-1"),
            )
        }

        val response = tool.run(
            context,
            mapOf("name" to "maps", "endpoint_url" to "https://example.com/mcp"),
        ) as Map<*, *>

        coVerify(exactly = 1) {
            useCase(
                sessionId = "session-1",
                name = "maps",
                endpointUrl = "https://example.com/mcp",
                transport = McpTransport.STREAMABLE_HTTP,
                description = "",
                bearerToken = "",
                headers = "",
                enabled = true,
            )
        }
        assertEquals(true, response["success"])
        assertEquals(true, response["updated"])
        assertEquals(true, response["conversation_activated"])
    }

    @Test
    fun reportsSavedButNotActivatedAsFailureWithHint() = runTest {
        val useCase = mockk<ConfigureMcpServerForConversationUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            McpManualConfigurationResult(
                serverId = "mcp-d",
                serverName = "maps",
                created = true,
                updated = false,
                conversationActivated = false,
            )
        val tool = McpManualConfigurationTool(useCase)
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", "session-1"),
            )
        }

        val response = tool.run(
            context,
            mapOf("name" to "maps", "endpoint_url" to "https://example.com/mcp"),
        ) as Map<*, *>

        assertEquals(false, response["success"])
        assertEquals(true, response["created"])
        assertTrue(
            (response["error"] as String).contains("saved"),
        )
    }

    @Test
    fun disabledServerReportsSuccessWithoutActivationHint() = runTest {
        val useCase = mockk<ConfigureMcpServerForConversationUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            McpManualConfigurationResult(
                serverId = "mcp-d",
                serverName = "maps",
                created = true,
                updated = false,
                conversationActivated = false,
            )
        val tool = McpManualConfigurationTool(useCase)
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", "session-1"),
            )
        }

        val response = tool.run(
            context,
            mapOf(
                "name" to "maps",
                "endpoint_url" to "https://example.com/mcp",
                "enabled" to false,
            ),
        ) as Map<*, *>

        coVerify(exactly = 1) {
            useCase(
                sessionId = "session-1",
                name = "maps",
                endpointUrl = "https://example.com/mcp",
                transport = McpTransport.STREAMABLE_HTTP,
                description = "",
                bearerToken = "",
                headers = "",
                enabled = false,
            )
        }
        assertEquals(true, response["success"])
        assertEquals(false, response["conversation_activated"])
        assertEquals(null, response["error"])
    }

    @Test
    fun rejectsBlankRequiredArguments() = runTest {
        val tool = McpManualConfigurationTool(mockk())
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", "session-1"),
            )
        }

        val response = tool.run(context, emptyMap()) as Map<*, *>

        assertEquals(false, response["success"])
        assertEquals("name and endpoint_url are required.", response["error"])
    }

    @Test
    fun rejectsInvalidTransport() = runTest {
        val useCase = mockk<ConfigureMcpServerForConversationUseCase>()
        val tool = McpManualConfigurationTool(useCase)
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", "session-1"),
            )
        }

        val response = tool.run(
            context,
            mapOf(
                "name" to "maps",
                "endpoint_url" to "https://example.com/mcp",
                "transport" to "carrier-pigeon",
            ),
        ) as Map<*, *>

        assertEquals(false, response["success"])
        assertTrue((response["error"] as String).contains("transport"))
    }

    @Test
    fun rejectsMissingPersistentSessionId() = runTest {
        val useCase = mockk<ConfigureMcpServerForConversationUseCase>()
        val tool = McpManualConfigurationTool(useCase)
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", ""),
            )
        }

        val response = tool.run(
            context,
            mapOf("name" to "maps", "endpoint_url" to "https://example.com/mcp"),
        ) as Map<*, *>

        assertEquals(false, response["success"])
        assertTrue((response["error"] as String).contains("session"))
    }

    @Test
    fun declarationRequiresNameAndEndpointUrl() {
        val tool = McpManualConfigurationTool(mockk())
        val declaration = requireNotNull(tool.declaration())

        assertEquals("configure_mcp_server", declaration.name)
        assertEquals(listOf("name", "endpoint_url"), declaration.parameters?.required)
        assertEquals(
            setOf(
                "name",
                "endpoint_url",
                "transport",
                "description",
                "bearer_token",
                "headers",
                "enabled",
            ),
            declaration.parameters?.properties?.keys,
        )
    }

    @Test
    fun processLlmRequestAppendsManualConfigInstructionAndToolDeclaration() = runTest {
        val tool = McpManualConfigurationTool(mockk())
        val request = LlmRequest(
            config = GenerateContentConfig(
                systemInstruction = Content(parts = listOf(Part(text = "Base instruction"))),
            ),
        )

        val processed = tool.processLlmRequest(mockk(relaxed = true), request)
        val systemText = processed.config.systemInstruction
            ?.parts
            .orEmpty()
            .mapNotNull(Part::text)
            .joinToString("\n")

        assertTrue(systemText.contains("Base instruction"))
        assertTrue(systemText.contains("configure_mcp_server"))
        assertTrue(systemText.contains("individual details"))
        assertEquals(
            listOf("configure_mcp_server"),
            processed.config.tools.orEmpty()
                .flatMap { it.functionDeclarations.orEmpty() }
                .map { it.name },
        )
    }
}
