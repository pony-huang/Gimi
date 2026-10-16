package dev.e2b.sdk

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RequestBoundaryTest {
    @Test fun `cancelling current requests leaves client reusable`() {
        MockWebServer().use { server ->
            server.start()
            E2BClient(E2BConfig.builder().apiKey("fake-key").apiUrl(server.url("/").toString()).build()).use { client ->
                val executor = Executors.newSingleThreadExecutor()
                try {
                    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                    val request = executor.submit<Throwable?> {
                        try { client.getInfo("sb"); null } catch (failure: E2BException) { failure }
                    }
                    assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                    client.cancelRequests()
                    assertTrue(request.get(2, TimeUnit.SECONDS) is TransportException)
                    server.enqueue(MockResponse().setBody("""{"sandboxID":"sb","templateID":"base","state":"running","cpuCount":2,"memoryMB":512,"diskSizeMB":2048,"metadata":{}}"""))
                    assertEquals("sb", client.getInfo("sb").sandboxId)
                } finally { executor.shutdownNow() }
            }
        }
    }

    @Test fun `bounded read accepts exact limit and empty files`() {
        withSandbox { sandbox, server ->
            server.enqueue(MockResponse().setBody("你好"))
            assertArrayEquals("你好".toByteArray(), sandbox.files.readBytes("a.txt", 6))
            server.enqueue(MockResponse().setBody(""))
            assertTrue(sandbox.files.readBytes("empty.txt", 1).isEmpty())
        }
    }

    @Test(expected = FileSizeLimitException::class)
    fun `bounded read rejects content length beyond limit`() = withSandbox { sandbox, server ->
        server.enqueue(MockResponse().setBody("too large"))
        sandbox.files.readBytes("a.txt", 3)
    }

    @Test(expected = FileSizeLimitException::class)
    fun `bounded read rejects chunked content beyond limit`() = withSandbox { sandbox, server ->
        server.enqueue(MockResponse().setChunkedBody("too large", 2))
        sandbox.files.readBytes("a.txt", 3)
    }

    private fun withSandbox(block: (Sandbox, MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            E2BClient(E2BConfig.builder().apiKey("fake-key").apiUrl(server.url("/").toString()).sandboxUrl(server.url("/").toString()).build()).use { client ->
                server.enqueue(MockResponse().setBody("""{"sandboxID":"sb","envdVersion":"0.5.7","envdAccessToken":"token"}"""))
                block(client.create(), server)
            }
        }
    }
}
