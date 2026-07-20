package github.ponyhuang.asssistantai.data.modelcatalog.remote

import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
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

class OpenAiCompatibleModelServiceGatewayTest {
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

    private fun gateway() = OpenAiCompatibleModelServiceGateway(
        okHttpClient = OkHttpClient(),
        ioDispatcher = Dispatchers.IO,
    )

    private fun service() = ModelService(
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
