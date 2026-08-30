package github.ponyhuang.gimi.data.agent.tools.mcp

import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.domain.mcp.model.McpCredentialUpdateResult
import github.ponyhuang.gimi.domain.mcp.usecase.UpdateMcpAuthorizationForConversationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class McpAuthorizationToolTest {
    @Test
    fun updatesPendingServerWithoutEchoingCredential() = runTest {
        val useCase = mockk<UpdateMcpAuthorizationForConversationUseCase>()
        coEvery { useCase("session-1", any()) } returns McpCredentialUpdateResult(
            updated = true,
            serverName = "zhihu-global-search",
        )
        val tool = McpAuthorizationTool(useCase)
        val context = mockk<ToolContext> {
            every { this@mockk.context.session } returns Session(
                SessionKey("gimi", "user", "session-1"),
            )
        }
        val authorization = "Authorization: Bearer actual-secret"

        val response = tool.run(context, mapOf("authorization" to authorization)) as Map<*, *>

        coVerify(exactly = 1) { useCase("session-1", authorization) }
        assertEquals(true, response["success"])
        assertEquals("zhihu-global-search", response["server"])
        assertFalse(response.toString().contains("actual-secret"))
    }

    @Test
    fun declarationRequiresOnlyAuthorization() {
        val declaration = requireNotNull(McpAuthorizationTool(mockk()).declaration())

        assertEquals("update_mcp_server_authorization", declaration.name)
        assertEquals(listOf("authorization"), declaration.parameters?.required)
        assertEquals(setOf("authorization"), declaration.parameters?.properties?.keys)
    }
}
