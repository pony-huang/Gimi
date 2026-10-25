package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.a2aproject.sdk.client.http.android.AndroidA2AHttpClient
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil
import org.a2aproject.sdk.spec.AgentCapabilities
import org.a2aproject.sdk.spec.AgentCard
import org.a2aproject.sdk.spec.AgentInterface
import org.a2aproject.sdk.spec.TransportProtocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 连通性探测的真实传输边界：卡片可解析即可达，失败摘要不含凭据。 */
class AdkRemoteAgentConnectionTesterTest {

    private lateinit var server: MockWebServer
    private val tester = AdkRemoteAgentConnectionTester(AndroidA2AHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun cardJson(): String {
        val url = server.url("/").toString()
        return JsonUtil.toJson(
            AgentCard.builder()
                .name("remote-agent")
                .description("Remote Agent")
                .url(url)
                .version("1.0.0")
                .defaultInputModes(listOf("text"))
                .defaultOutputModes(listOf("text"))
                .skills(listOf())
                .supportedInterfaces(listOf(AgentInterface(TransportProtocol.JSONRPC.asString(), url)))
                .capabilities(AgentCapabilities.builder().streaming(false).build())
                .build(),
        )
    }

    @Test
    fun parsableCardIsReachable() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(cardJson()))

        val result = tester.test(RemoteAgentConfig(endpointUrl = server.url("/").toString()))

        assertTrue(result.reachable)
        assertEquals("/.well-known/agent-card.json", server.takeRequest().path)
    }

    @Test
    fun reachableProbeCarriesCardSnapshot() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(cardJson()))

        val result = tester.test(RemoteAgentConfig(endpointUrl = server.url("/").toString()))

        val card = requireNotNull(result.card)
        assertEquals("remote-agent", card.name)
        assertEquals("1.0.0", card.version)
        assertEquals(listOf("text"), card.defaultInputModes)
        assertTrue(card.skills.isEmpty())
        assertEquals(false, card.supportsStreaming)
    }

    @Test
    fun httpErrorReportsSummary() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = tester.test(RemoteAgentConfig(endpointUrl = server.url("/").toString()))

        assertFalse(result.reachable)
        assertTrue(result.errorMessage?.contains("HTTP 404") == true)
    }

    @Test
    fun malformedCardIsUnreachable() = runBlocking {
        server.enqueue(MockResponse().setBody("{ not json"))

        val result = tester.test(RemoteAgentConfig(endpointUrl = server.url("/").toString()))

        assertFalse(result.reachable)
    }

    @Test
    fun bearerTokenIsSentOnProbe() = runBlocking {
        server.enqueue(MockResponse().setBody(cardJson()))

        tester.test(
            RemoteAgentConfig(endpointUrl = server.url("/").toString(), bearerToken = "tok"),
        )

        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }
}
