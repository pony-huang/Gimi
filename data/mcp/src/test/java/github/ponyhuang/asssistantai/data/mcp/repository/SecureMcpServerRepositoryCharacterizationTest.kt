package github.ponyhuang.asssistantai.data.mcp.repository

import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SecureMcpServerRepositoryCharacterizationTest {
    @Test
    fun importPortableJsonAcceptsHttpAndSseAndSkipsStdio() {
        val storage = FakeStorage()
        val repository = SecureMcpServerRepository(storage)

        val result = repository.importJson(
            """
            {
              "mcpServers": {
                "http-server": {
                  "type": "streamable_http",
                  "url": "https://example.com/mcp",
                  "headers": { "X-Api-Key": "secret" }
                },
                "sse-server": {
                  "type": "sse",
                  "url": "https://example.com/sse"
                },
                "stdio-server": {
                  "command": "node",
                  "args": ["server.js"]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(2, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(2, repository.currentServers().size)
        assertEquals(McpTransport.STREAMABLE_HTTP, repository.currentServers()[0].transport)
        assertEquals("X-Api-Key=secret", repository.currentServers()[0].headers)
        assertEquals(McpTransport.SSE, repository.currentServers()[1].transport)
        assertNotNull(storage.value)
    }

    @Test
    fun importPortableJsonDefaultsToHttpWhenTypeIsMissing() {
        val storage = FakeStorage()
        val repository = SecureMcpServerRepository(storage)

        val result = repository.importJson(
            """
            {
              "mcpServers": {
                "amap-maps-streamableHTTP": {
                  "url": "https://mcp.amap.com/mcp?key=xxx"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(1, repository.currentServers().size)
        assertEquals(McpTransport.STREAMABLE_HTTP, repository.currentServers()[0].transport)
        assertEquals("amap-maps-streamableHTTP", repository.currentServers()[0].name)
        assertEquals("https://mcp.amap.com/mcp?key=xxx", repository.currentServers()[0].endpointUrl)
    }

    @Test
    fun invalidJsonReturnsLocalizedErrorWithoutChangingServers() {
        val repository = SecureMcpServerRepository(FakeStorage())

        val result = repository.importJson("not-json")

        assertEquals("JSON 格式无效", result.error)
        assertEquals(emptyList<Any>(), repository.currentServers())
    }

    private class FakeStorage(
        var value: String? = null,
    ) : McpServerStorage {
        override fun read() = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
