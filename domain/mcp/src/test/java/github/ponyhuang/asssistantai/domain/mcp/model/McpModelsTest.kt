package github.ponyhuang.asssistantai.domain.mcp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpModelsTest {

    @Test
    fun newServersAreEnabledWithStreamableHttpTransportByDefault() {
        val server = McpServer()

        assertTrue(server.isEnabled)
        assertEquals(McpTransport.STREAMABLE_HTTP, server.transport)
        assertEquals("", server.name)
        assertEquals("", server.endpointUrl)
        assertEquals("", server.bearerToken)
        assertEquals("", server.headers)
    }

    @Test
    fun serverIdsAreGeneratedUniquePerInstance() {
        assertNotEquals(McpServer().id, McpServer().id)
    }

    @Test
    fun transportsCoverSseAndStreamableHttp() {
        assertEquals(
            setOf(McpTransport.SSE, McpTransport.STREAMABLE_HTTP),
            McpTransport.entries.toSet(),
        )
    }

    @Test
    fun importResultMessageSummarizesImportedCount() {
        assertEquals("已导入 3 个 MCP 服务", McpImportResult(imported = 3).message)
    }

    @Test
    fun importResultMessageMentionsSkippedEntriesOnlyWhenPresent() {
        assertEquals(
            "已导入 2 个 MCP 服务；跳过 1 个不受支持的 stdio 或无效配置",
            McpImportResult(imported = 2, skipped = 1).message,
        )
        assertEquals(
            "已导入 2 个 MCP 服务",
            McpImportResult(imported = 2, skipped = 0).message,
        )
    }

    @Test
    fun importResultErrorReplacesSuccessMessage() {
        val result = McpImportResult(imported = 2, skipped = 1, error = "解析失败")

        assertEquals("解析失败", result.message)
    }
}
