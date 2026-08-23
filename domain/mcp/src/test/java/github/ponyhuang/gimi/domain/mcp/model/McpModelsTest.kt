package github.ponyhuang.gimi.domain.mcp.model

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
    fun importResultCombinesCreatedAndUpdatedCounts() {
        assertEquals(5, McpImportResult(created = 3, updated = 2).imported)
    }
}
