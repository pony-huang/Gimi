package github.ponyhuang.gimi.data.sandbox

import dev.e2b.sdk.E2BClient
import dev.e2b.sdk.E2BConfig
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplate
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateException
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateFailure
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SdkSandboxTemplateRepositoryTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: SdkSandboxTemplateRepository
    @Before fun setup() {
        server.start()
        repository = SdkSandboxTemplateRepository(scope) { _, apiKey ->
            E2BClient(E2BConfig.builder().apiKey(apiKey).apiUrl(server.url("/").toString()).requestTimeoutSeconds(2).build())
        }
    }
    @After fun cleanup() { scope.cancel(); server.shutdown() }

    @Test fun collectsAllPagesAndDeduplicatesWithoutCreatingSandbox() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"templateID":"one","names":["tools"],"buildStatus":"building"}]""")
            .setHeader("X-Next-Token", "second"))
        server.enqueue(MockResponse().setBody("""[{"templateID":"one","names":["tools"],"buildStatus":"ready"},{"templateID":"two"}]"""))
        assertEquals(listOf(SandboxTemplate("one", listOf("tools"), "ready"), SandboxTemplate("two", emptyList(), null)),
            repository.listTemplates("e2b.app", "draft-key"))
        repeat(2) { assertEquals("/v2/templates", server.takeRequest().requestUrl!!.encodedPath) }
        assertEquals(2, server.requestCount)
    }
    @Test fun laterPageFailureDoesNotReturnPartialListAndErrorsAreRedacted() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"templateID":"one"}]""").setHeader("X-Next-Token", "second"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("draft-key"))
        assertFailure(SandboxTemplateFailure.AUTHENTICATION)
    }
    @Test fun repeatedPaginationTokenFailsInsteadOfLooping() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody("[]").setHeader("X-Next-Token", "same")) }
        assertFailure(SandboxTemplateFailure.PROTOCOL)
        assertEquals(2, server.requestCount)
    }
    @Test fun mapsRateLimitProtocolAndNetworkFailures() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("draft-key"))
        assertFailure(SandboxTemplateFailure.RATE_LIMIT)
        server.enqueue(MockResponse().setBody("{}"))
        assertFailure(SandboxTemplateFailure.PROTOCOL)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertFailure(SandboxTemplateFailure.NETWORK)
    }
    @Test fun cancellationStopsQueryWithoutCancellingOtherQueries() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val query = async { repository.listTemplates("e2b.app", "draft-key") }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
        query.cancel(); query.join()
        assertTrue(query.isCancelled)
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(repository.listTemplates("e2b.app", "other-key").isEmpty())
    }
    private suspend fun assertFailure(expected: SandboxTemplateFailure) {
        try { repository.listTemplates("e2b.app", "draft-key"); fail("Expected query failure") }
        catch (failure: SandboxTemplateException) {
            assertEquals(expected, failure.failure)
            assertFalse(failure.toString().contains("draft-key"))
        }
    }
}
