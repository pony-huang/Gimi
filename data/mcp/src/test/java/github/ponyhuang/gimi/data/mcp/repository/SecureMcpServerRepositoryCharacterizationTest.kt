package github.ponyhuang.gimi.data.mcp.repository

import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpTransport
import java.security.InvalidKeyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureMcpServerRepositoryCharacterizationTest {
    @Test
    fun retryWithFreshKeyRetriesOnceAfterInvalidKey() {
        var attempts = 0
        var resetCount = 0

        val result = retryWithFreshKeyAfterInvalidKey(
            resetKey = { resetCount++ },
            operation = {
                attempts++
                if (attempts == 1) throw InvalidKeyException("key invalidated")
                "encrypted"
            },
        )

        assertEquals("encrypted", result)
        assertEquals(2, attempts)
        assertEquals(1, resetCount)
    }

    @Test
    fun retryWithFreshKeyDoesNotResetForOtherFailures() {
        var resetCount = 0

        assertThrows(IllegalStateException::class.java) {
            retryWithFreshKeyAfterInvalidKey(
                resetKey = { resetCount++ },
                operation = { throw IllegalStateException("storage unavailable") },
            )
        }

        assertEquals(0, resetCount)
    }

    @Test
    fun importCurlCreatesSseServerAndMarksPlaceholderAuthorizationAsPending() {
        val repository = SecureMcpServerRepository(FakeStorage())

        val result = repository.importConfiguration(
            """
            curl -N 'https://developer.zhihu.com/api/mcp/global_search/v1/sse' \
              -H 'Authorization: Bearer <your_access_secret>' \
              -H 'Accept: text/event-stream'
            """.trimIndent(),
        )

        val server = repository.currentServers().single()
        assertEquals(1, result.created)
        assertEquals(setOf(server.id), result.credentialRequiredServerIds)
        assertEquals("zhihu-global-search", server.name)
        assertEquals(McpTransport.SSE, server.transport)
        assertEquals(
            "https://developer.zhihu.com/api/mcp/global_search/v1/sse",
            server.endpointUrl,
        )
        assertEquals("Accept=text/event-stream", server.headers)
    }

    @Test
    fun updateAuthorizationReplacesOnlyAuthorizationAndPreservesOtherHeaders() {
        val repository = SecureMcpServerRepository(FakeStorage())
        val imported = repository.importConfiguration(
            """
            curl -N 'https://developer.zhihu.com/api/mcp/global_search/v1/sse' \
              -H 'Authorization: Bearer <your_access_secret>' \
              -H 'Accept: text/event-stream'
            """.trimIndent(),
        )
        val serverId = imported.credentialRequiredServerIds.single()

        val updated = repository.updateAuthorization(serverId, "Bearer actual-secret")

        assertEquals(true, updated)
        assertEquals(
            "Accept=text/event-stream\nAuthorization=Bearer actual-secret",
            repository.server(serverId)?.headers,
        )
    }

    @Test
    fun reimportingCredentialTemplateDoesNotEraseExistingAuthorization() {
        val repository = SecureMcpServerRepository(FakeStorage())
        repository.save(
            McpServer(
                id = "stable-id",
                name = "zhihu-global-search",
                endpointUrl = "https://old.example.com/sse",
                transport = McpTransport.SSE,
                headers = "Authorization=Bearer existing-secret\nX-Trace=keep-if-not-replaced",
            ),
        )

        val result = repository.importConfiguration(
            """
            curl -N 'https://developer.zhihu.com/api/mcp/global_search/v1/sse' \
              -H 'Authorization: Bearer <your_access_secret>' \
              -H 'Accept: text/event-stream'
            """.trimIndent(),
        )

        assertEquals(emptySet<String>(), result.credentialRequiredServerIds)
        assertEquals(
            "Accept=text/event-stream\nAuthorization=Bearer existing-secret",
            repository.server("stable-id")?.headers,
        )
    }

    @Test
    fun importPortableJsonUpdatesSameNameWithoutCreatingDuplicate() {
        val storage = FakeStorage()
        val repository = SecureMcpServerRepository(storage)
        val original = McpServer(
            id = "stable-id",
            name = "github",
            description = "Keep this description",
            endpointUrl = "https://old.example.com/mcp",
            bearerToken = "old-token",
            isEnabled = false,
        )
        repository.save(original)
        val revisionBeforeImport = repository.revision.value

        val result = repository.importJson(
            """
            {
              "mcpServers": {
                " github ": {
                  "type": "sse",
                  "url": " https://new.example.com/sse ",
                  "headers": { "Authorization": "Bearer new-token" }
                },
                "maps": {
                  "url": "https://maps.example.com/mcp"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(1, result.created)
        assertEquals(1, result.updated)
        assertEquals(setOf("stable-id", repository.currentServers()[1].id), result.affectedServerIds)
        assertEquals(revisionBeforeImport + 1, repository.revision.value)
        assertEquals(2, repository.currentServers().size)
        assertEquals(
            original.copy(
                endpointUrl = "https://new.example.com/sse",
                transport = McpTransport.SSE,
                bearerToken = "",
                headers = "Authorization=Bearer new-token",
            ),
            repository.currentServers().first(),
        )
    }

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
