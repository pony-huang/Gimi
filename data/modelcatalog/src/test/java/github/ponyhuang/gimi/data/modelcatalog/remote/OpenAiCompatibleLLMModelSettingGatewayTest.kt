package github.ponyhuang.gimi.data.modelcatalog.remote

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import kotlinx.coroutines.Dispatchers
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
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = server.url("/anthropic").toString(),
        groups = emptyList(),
    )
}
