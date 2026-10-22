package github.ponyhuang.gimi.data.modelcatalog.remote

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OpenAiCompatibleLLMModelSettingGatewayTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validateConnectionOnlyAcceptsHttp200AndPreservesHeaders() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val gateway = gateway()

        assertTrue(gateway.validateConnection(service()))

        val request = server.takeRequest()
        assertEquals("/models", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("Bearer secret", request.getHeader("Authorization"))
    }

    @Test
    fun validateConnectionRejectsOtherStatusCodes() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        assertFalse(gateway().validateConnection(service()))
    }

    @Test
    fun fetchModelsUsesInjectedHttpClient() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"id":"model-a"},{"id":"model-b"}]}"""),
        )
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                calls.incrementAndGet()
                chain.proceed(chain.request())
            }
            .build()
        val gateway = OpenAiCompatibleModelServiceGateway(client, Dispatchers.IO)

        val models = gateway.fetchModels(service())

        assertEquals(listOf("model-a", "model-b"), models.map { it.id })
        assertEquals(1, calls.get())
    }

    @Test
    fun anthropicUsesSelectedAddressAndProtocolHeaders() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"claude-test"}]}"""))
        val models = gateway().fetchModels(service().copy(apiProtocol = ApiProtocol.Anthropic))
        assertEquals(listOf("claude-test"), models.map { it.id })
        val request = server.takeRequest()
        assertEquals("/anthropic/v1/models", request.path)
        assertEquals("secret", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test
    fun anthropicAddressAlreadyEndingInV1IsNotDuplicated() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(gateway().validateConnection(service().copy(apiProtocol = ApiProtocol.Anthropic,
            anthropicBaseUrl = server.url("/v1/").toString())))
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun failedFetchDoesNotReturnModels() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val failure = runCatching { gateway().fetchModels(service()) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("Model list request failed with HTTP 401", failure?.message)
    }

    @Test
    fun fetchFiltersEmptyAndDuplicateModelIds() = runTest {
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"data":[{"id":""},{"id":"a"},{"id":"a"}]}"""))
        assertEquals(listOf("a"), gateway().fetchModels(service()).map { it.id })
    }

    @Test
    fun cancellationStopsAnOutstandingModelRequest() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.IO) { gateway().fetchModels(service()) }
        val request = withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
        assertTrue(request != null)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    private fun gateway() = OpenAiCompatibleModelServiceGateway(
        okHttpClient = OkHttpClient(),
        ioDispatcher = Dispatchers.IO,
    )

    private fun service() = LLMModelSetting(
        id = "test",
        name = "Test",
        isEnabled = true,
        apiKey = " secret ",
        apiBaseUrl = server.url("/").toString(),
        apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = server.url("/anthropic").toString(),
        groups = emptyList(),
    )
}
