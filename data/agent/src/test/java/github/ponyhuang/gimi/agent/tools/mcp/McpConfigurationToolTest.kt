package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.domain.mcp.model.McpConversationImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.mcp.usecase.ImportMcpServersForConversationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigurationToolTest {
    @Test
    fun importsForCurrentSessionAndReturnsOnlySafeSummary() = runTest {
        val useCase = mockk<ImportMcpServersForConversationUseCase>()
        val repository = mockk<McpRepository>() {
            every { revision } returns MutableStateFlow(1L)
            every { currentServers() } returns listOf(
                McpServer(
                    id = "mcp-d",
                    name = "maps",
                    endpointUrl = "https://secret.example.com/mcp",
                    headers = "Authorization=Bearer secret",
                ),
            )
        }
        coEvery { useCase("session-1", any()) } returns McpConversationImportResult(
            importResult = McpImportResult(
                created = 1,
                affectedServerIds = setOf("mcp-d"),
                credentialRequiredServerIds = setOf("mcp-d"),
            ),
            conversationActivated = true,
        )
        val tool = McpConfigurationTool(useCase, repository)
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", "session-1"),
            )
        }
        val content = "curl 'https://secret.example.com/mcp'"

        val response = tool.run(context, mapOf("config_content" to content)) as Map<*, *>

        coVerify(exactly = 1) { useCase("session-1", content) }
        assertEquals(true, response["success"])
        assertEquals(1, response["created"])
        assertEquals(true, response["credentials_required"])
        assertEquals(listOf("maps"), response["servers"])
        assertFalse(response.toString().contains("secret.example.com"))
        assertFalse(response.toString().contains("Bearer secret"))
    }

    @Test
    fun declarationRequiresOnlyRawConfigurationContent() {
        val tool = McpConfigurationTool(mockk(), mockk())
        val declaration = requireNotNull(tool.declaration())

        assertEquals("import_mcp_servers", declaration.name)
        assertEquals(listOf("config_content"), declaration.parameters?.required)
        assertEquals(setOf("config_content"), declaration.parameters?.properties?.keys)
    }

    @Test
    fun processLlmRequestAppendsMcpRecognitionInstructionAndToolDeclaration() = runTest {
        val tool = McpConfigurationTool(mockk(), mockk())
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
        assertTrue(systemText.contains("clearly contains an MCP server configuration"))
        assertTrue(systemText.contains("JSON or curl"))
        assertTrue(systemText.contains("update_mcp_server_authorization"))
        assertTrue(systemText.contains("Do not call it for unrelated JSON"))
        assertEquals(
            listOf("import_mcp_servers"),
            processed.config.tools.orEmpty()
                .flatMap { it.functionDeclarations.orEmpty() }
                .map { it.name },
        )
    }
}
