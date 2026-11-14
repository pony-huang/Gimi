package github.ponyhuang.gimi.data.modelcatalog.remote

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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

        assertEquals(ServiceConnectionResult.Succeeded, gateway.validateConnection(connection()))

        val request = server.takeRequest()
        assertEquals("/models", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("Bearer secret", request.getHeader("Authorization"))
    }

    @Test
    fun validateConnectionRejectsOtherStatusCodes() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        assertEquals(ServiceConnectionResult.NetworkFailed, gateway().validateConnection(connection()))
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

        val models = gateway.fetchModels(connection())

        assertEquals(listOf("model-a", "model-b"), models.map { it.id })
        assertEquals(1, calls.get())
    }

    @Test
    fun deepSeekAnthropicUsesOpenAiCatalogForConnectionAndModels() = runTest {
        // 兼容 Messages 不代表兼容 Anthropic 模型目录；Anthropic 入口 404 时剥掉 /anthropic 后缀回落 OpenAI 根地址。
        val deepSeek = connection(
            templateId = "deepseek",
            protocol = ApiProtocol.Anthropic,
            endpoint = server.url("/anthropic/").toString(),
        )
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody("""{"data":[{"id":"deepseek-test"}]}"""))
        }
        val gateway = gateway()
        assertEquals(ServiceConnectionResult.Succeeded, gateway.validateConnection(deepSeek))
        assertEquals(listOf("deepseek-test"), gateway.fetchModels(deepSeek).map { it.id })
        repeat(2) {
            // 回退顺序：Anthropic 入口目录 → OpenAI 根 /v1/models → OpenAI 根 /models。
            assertEquals("/anthropic/v1/models", server.takeRequest().path)
            assertEquals("/v1/models", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("/models", request.path)
            assertEquals("Bearer secret", request.getHeader("Authorization"))
            assertEquals(null, request.getHeader("x-api-key"))
            assertEquals(null, request.getHeader("anthropic-version"))
        }
    }

    @Test
    fun anthropicCatalogAuthFailureDoesNotFallBack() = runTest {
        // 401/403 说明端点存在但密钥无效，必须如实报告鉴权失败而不是继续探测其它地址。
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(
            ServiceConnectionResult.AuthFailed,
            gateway().validateConnection(
                connection(protocol = ApiProtocol.Anthropic, endpoint = server.url("/anthropic/").toString()),
            ),
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun anthropicUsesSelectedAddressAndProtocolHeaders() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"claude-test"}]}"""))
        val models = gateway().fetchModels(connection(protocol = ApiProtocol.Anthropic, endpoint = server.url("/anthropic").toString()))
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
        assertEquals(
            ServiceConnectionResult.Succeeded,
            gateway().validateConnection(
                connection(protocol = ApiProtocol.Anthropic, endpoint = server.url("/v1/").toString()),
            ),
        )
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun speechConnectionsDoNotProvideCatalogEndpoints() = runTest {
        val failure = runCatching {
            gateway().fetchModels(connection(capability = ServiceCapability.Synthesis))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun failedFetchDoesNotReturnModels() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val failure = runCatching { gateway().fetchModels(connection()) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("Model list request failed with HTTP 401", failure?.message)
    }

    @Test
    fun fetchFiltersEmptyAndDuplicateModelIds() = runTest {
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"data":[{"id":""},{"id":"a"},{"id":"a"}]}"""))
        assertEquals(listOf("a"), gateway().fetchModels(connection()).map { it.id })
    }

    @Test
    fun cancellationStopsAnOutstandingModelRequest() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.IO) { gateway().fetchModels(connection()) }
        val request = withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
        assertTrue(request != null)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    private fun gateway() = OpenAiCompatibleModelServiceGateway(
        okHttpClient = OkHttpClient(),
        ioDispatcher = Dispatchers.IO,
    )

    private fun connection(
        templateId: String? = "test",
        protocol: ApiProtocol = ApiProtocol.OpenaiChatcompletions,
        endpoint: String = server.url("/").toString(),
        capability: ServiceCapability = ServiceCapability.Conversation,
    ) = ResolvedServiceConnection(
        instanceId = "instance-1",
        templateId = templateId,
        connectionId = "conn-1",
        capability = capability,
        protocol = protocol,
        endpoint = endpoint,
        apiKey = " secret ",
        workspaceId = null,
        model = Model("gpt-4.1", "gpt-4.1", capabilities = setOf(ModelCapability.LLM)),
    )
}
