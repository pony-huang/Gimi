package github.ponyhuang.gimi.data.sandbox

import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxOperation
import github.ponyhuang.gimi.domain.sandbox.SandboxOutput
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SandboxHttpEndpointTest {
    @Test fun configuredHttpUrlsRouteControlFilesAndInterpreterWithoutLeakingApiKey() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val api = MockWebServer()
        val proxy = MockWebServer()
        api.start(); proxy.start()
        val gateway = SdkSandboxGateway(SandboxConnection(
            apiUrl = api.url("/Control/").toString(), apiKey = "control-key",
            sandboxUrl = proxy.url("/Proxy/").toString(),
        ), scope)
        try {
            api.enqueue(MockResponse().setBody("""{"sandboxID":"local-sb","domain":"e2b.app","envdVersion":"0.5.7","envdAccessToken":"sandbox-token"}"""))
            gateway.create()
            val create = api.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/Control/v2/sandboxes", create.path)
            assertEquals("control-key", create.getHeader("X-API-Key"))

            proxy.enqueue(MockResponse().setBody("hello"))
            assertEquals(SandboxOutput.ReadFile("/tmp/a", "hello"), gateway.execute(SandboxOperation.ReadFile("/tmp/a")))
            val read = proxy.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/Proxy/files", read.requestUrl!!.encodedPath)
            assertEquals("49983", read.getHeader("E2b-Sandbox-Port"))
            assertEquals("local-sb", read.getHeader("E2b-Sandbox-Id"))
            assertEquals("sandbox-token", read.getHeader("X-Access-Token"))
            assertNull(read.getHeader("X-API-Key"))

            proxy.enqueue(MockResponse().setBody("{\"type\":\"result\",\"text\":\"42\",\"is_main_result\":true}\n"))
            val result = gateway.execute(SandboxOperation.Code("6 * 7")) as SandboxOutput.Code
            assertEquals("42", result.text)
            val code = proxy.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/Proxy/execute", code.path)
            assertEquals("49999", code.getHeader("E2b-Sandbox-Port"))
            assertNull(code.getHeader("X-API-Key"))
        } finally {
            api.enqueue(MockResponse().setResponseCode(204))
            gateway.shutdown()
            scope.cancel(); api.shutdown(); proxy.shutdown()
        }
    }

    @Test fun templateQueryUsesConfiguredHttpApiAndPathPrefix() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val api = MockWebServer()
        api.start()
        try {
            api.enqueue(MockResponse().setBody("[]"))
            assertTrue(SdkSandboxTemplateRepository(scope).listTemplates(api.url("/Control/").toString(), "key").isEmpty())
            val request = api.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/Control/v2/templates", request.requestUrl!!.encodedPath)
            assertEquals("key", request.getHeader("X-API-Key"))
        } finally {
            scope.cancel(); api.shutdown()
        }
    }
}
