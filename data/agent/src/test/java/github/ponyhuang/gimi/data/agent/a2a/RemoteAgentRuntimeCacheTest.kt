package github.ponyhuang.gimi.data.agent.a2a

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.apps.App
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentChatExecution
import github.ponyhuang.gimi.data.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.model.SkippedRemoteAgent
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentRepository
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentSkipReporter
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 远程 A2A agent 运行时缓存的装配/降级/缓存边界：经真实 [AndroidA2AHttpClient] 对
 * MockWebServer 拉取 agent card，覆盖成功装配、失败跳过上报、配置身份缓存与认证头注入。
 */
class RemoteAgentRuntimeCacheTest {

    private class FakeRemoteAgentRepository(
        initial: List<RemoteAgentConfig> = emptyList(),
    ) : RemoteAgentRepository {
        val agents = MutableStateFlow(initial)
        override val revision = MutableStateFlow(0L)

        override fun observeAgents() = agents

        override fun currentAgents(): List<RemoteAgentConfig> = agents.value

        override fun agent(id: String): RemoteAgentConfig? = agents.value.firstOrNull { it.id == id }

        override fun save(agent: RemoteAgentConfig) {
            agents.value = agents.value.filterNot { it.id == agent.id } + agent
            revision.value += 1
        }

        override fun delete(id: String) {
            agents.value = agents.value.filterNot { it.id == id }
            revision.value += 1
        }
    }

    private class FakeRemoteAgentSkipReporter : RemoteAgentSkipReporter {
        val skippedFlow = MutableStateFlow<List<SkippedRemoteAgent>>(emptyList())
        override val skipped = skippedFlow

        override fun publish(skipped: List<SkippedRemoteAgent>) {
            skippedFlow.value = skipped
        }
    }

    private lateinit var server: MockWebServer
    private val repository = FakeRemoteAgentRepository()
    private val skipReporter = FakeRemoteAgentSkipReporter()
    private val spec = AgentBuildSpec(pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun cache(): RemoteAgentRuntimeCache =
        RemoteAgentRuntimeCache(repository, AndroidA2AHttpClient(), skipReporter, RemoteAgentCardFetcher())

    private fun enqueueCard(name: String = "remote-agent") {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(cardJson(name)))
    }

    /** 构造 a2a-java 1.0 规格的最小合法 agent card（与 adk-kotlin 测试夹具同构）。 */
    private fun cardJson(name: String): String {
        val url = server.url("/").toString()
        return JsonUtil.toJson(
            AgentCard.builder()
                .name(name)
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
    fun buildsSubAgentForEnabledConfig() = runBlocking {
        enqueueCard()
        repository.save(RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString()))

        val agents = cache().currentSubAgents(spec)

        assertEquals(1, agents.size)
        assertEquals("weather_agent", agents.single().name)
        assertTrue(skipReporter.skipped.value.isEmpty())
        assertEquals("/.well-known/agent-card.json", server.takeRequest().path)
    }

    @Test
    fun usesIdAsAgentNameWhenNameBlank() = runBlocking {
        enqueueCard()
        val config = RemoteAgentConfig(name = "", endpointUrl = server.url("/").toString())
        repository.save(config)

        val agents = cache().currentSubAgents(spec)

        assertEquals(config.id, agents.single().name)
    }

    @Test
    fun skipsAndReportsWhenCardUnavailable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        repository.save(RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString()))

        val agents = cache().currentSubAgents(spec)

        assertTrue(agents.isEmpty())
        val skipped = skipReporter.skipped.value
        assertEquals(1, skipped.size)
        assertEquals("weather_agent", skipped.single().displayName)
        assertTrue(skipped.single().reason?.contains("HTTP 404") == true)
    }

    @Test
    fun ignoresDisabledConfigs() = runBlocking {
        repository.save(
            RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString(), isEnabled = false),
        )

        val agents = cache().currentSubAgents(spec)

        assertTrue(agents.isEmpty())
        assertEquals(0, server.requestCount)
        assertTrue(skipReporter.skipped.value.isEmpty())
    }

    @Test
    fun reusesResolvedCardAcrossBuilds() = runBlocking {
        enqueueCard()
        repository.save(RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString()))
        val runtimeCache = cache()

        runtimeCache.currentSubAgents(spec)
        runtimeCache.currentSubAgents(spec)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun rebuildAssemblyGetsFreshInstancesAndCanReattachTrees() = runBlocking {
        enqueueCard()
        repository.save(RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString()))
        val runtimeCache = cache()

        val first = runtimeCache.currentSubAgents(spec)
        val second = runtimeCache.currentSubAgents(spec)

        // 卡片缓存复用（仅一次网络请求），但 agent 实例必须按次新建：
        // BaseAgent 挂树时写入 parentAgent，同一实例二次挂载抛 "already has a parent"。
        assertNotSame(first.single(), second.single())
        LlmAgent(name = "Tree1", model = mockk<Model>(), subAgents = first)
        LlmAgent(name = "Tree2", model = mockk<Model>(), subAgents = second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun deduplicatesAgentsWithSameName() = runBlocking {
        enqueueCard()
        repository.save(RemoteAgentConfig(id = "first", name = "same_name", endpointUrl = server.url("/").toString()))
        repository.save(
            RemoteAgentConfig(id = "second", name = "same_name", endpointUrl = "http://other.example.com"),
        )

        val agents = cache().currentSubAgents(spec)

        assertEquals(1, agents.size)
        assertEquals(1, server.requestCount)
        val skipped = skipReporter.skipped.value.single()
        assertEquals("second", skipped.remoteAgentId)
        assertEquals("duplicate agent name", skipped.reason)
    }

    @Test
    fun sendsBearerTokenHeaderOnCardFetch() = runBlocking {
        enqueueCard()
        repository.save(
            RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString(), bearerToken = "tok"),
        )

        cache().currentSubAgents(spec)

        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun rebuildsAfterConfigChange() = runBlocking {
        enqueueCard()
        val config = RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString())
        repository.save(config)
        val runtimeCache = cache()
        runtimeCache.currentSubAgents(spec)

        // 第二个实例模拟远端下线：地址变化使缓存身份失效，重新解析 card 失败后跳过。
        val dead = MockWebServer()
        dead.start()
        dead.enqueue(MockResponse().setResponseCode(500))
        repository.save(config.copy(endpointUrl = dead.url("/").toString()))
        val agents = runtimeCache.currentSubAgents(spec)
        assertEquals("/.well-known/agent-card.json", dead.takeRequest().path)
        dead.shutdown()

        assertTrue(agents.isEmpty())
        assertTrue(skipReporter.skipped.value.single().reason?.contains("HTTP 500") == true)
    }

    @Test
    fun invalidAgentAssemblyDoesNotBlockHealthyAgents() = runBlocking {
        enqueueCard()
        enqueueCard()
        repository.save(RemoteAgentConfig(name = "invalid/name", endpointUrl = server.url("/").toString()))
        repository.save(RemoteAgentConfig(name = "healthy", endpointUrl = server.url("/").toString()))

        val agents = cache().currentSubAgents(spec)

        assertEquals(listOf("healthy"), agents.map { it.name })
        assertEquals("invalid/name", skipReporter.skipped.value.single().displayName)
    }

    @Test
    fun failedRemoteCallReturnsToAssistantWithoutResendingAndNextTurnWorks() = runBlocking {
        enqueueCard()
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("still unavailable"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable in other session"))
        repository.save(RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString()))
        val subAgents = cache().currentSubAgents(spec)
        val requests = mutableListOf<LlmRequest>()
        val model = object : Model {
            override val name = "recovery-fixture"
            override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
                requests += request
                val part = if (requests.size in setOf(1, 2, 4, 6)) {
                    Part(functionCall = FunctionCall(
                        id = "transfer-${requests.size}",
                        name = "transfer_to_agent",
                        args = mapOf("agent_name" to "weather_agent"),
                    ))
                } else Part(text = "assistant continued")
                emit(LlmResponse(content = Content(role = "model", parts = listOf(part))))
            }
        }
        val runner = InMemoryRunner(App(
            appName = AgentChatRunner.APP_NAME,
            rootAgent = LlmAgent(
                name = "Assistant",
                model = model,
                subAgents = subAgents,
                beforeModelCallbacks = listOf(RemoteAgentFailureRecovery.beforeModel),
                beforeToolCallbacks = listOf(RemoteAgentFailureRecovery.beforeTool),
            ),
            resumabilityConfig = ResumabilityConfig(isResumable = true),
        ))
        val execution = AgentChatExecution("user", "recovery-session", runner, emptyMap())

        val first = execution.send("use weather").toList()
        val next = execution.send("continue chat").toList()
        val other = AgentChatExecution("user", "other-session", runner, emptyMap()).send("use weather").toList()

        assertTrue(first.any { it.author == "Assistant" && it.content?.parts?.any { part -> part.text == "assistant continued" } == true })
        assertTrue(next.any { it.author == "Assistant" && it.content?.parts?.any { part -> part.text == "assistant continued" } == true })
        assertTrue(requests[1].contents.toString().contains("remote_agent_unavailable"))
        assertTrue(requests[1].config.tools.orEmpty().flatMap { it.functionDeclarations.orEmpty() }
            .none { it.name == "transfer_to_agent" })
        assertTrue(requests[3].config.tools.orEmpty().flatMap { it.functionDeclarations.orEmpty() }
            .any { it.name == "transfer_to_agent" })
        assertTrue(other.any { it.author == "Assistant" && it.content?.parts?.any { part -> part.text == "assistant continued" } == true })
        assertEquals(4, server.requestCount) // 一次卡片、每个新轮次至多一次任务；同轮重复委派不触网。
        runner.close()
    }

    @Test
    fun unavailableCardInvalidatesRunnerRevisionAndCanRecoverOnNextBuild() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        enqueueCard()
        repository.save(RemoteAgentConfig(name = "weather_agent", endpointUrl = server.url("/").toString()))
        val runtimeCache = cache()
        val revision = runtimeCache.retryRevision

        assertTrue(runtimeCache.currentSubAgents(spec).isEmpty())
        assertTrue(runtimeCache.retryRevision > revision)
        assertEquals("weather_agent", runtimeCache.currentSubAgents(spec).single().name)
        assertTrue(skipReporter.skipped.value.isEmpty())
    }
}
