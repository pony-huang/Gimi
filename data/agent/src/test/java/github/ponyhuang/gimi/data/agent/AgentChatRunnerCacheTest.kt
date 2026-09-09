package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.EventActions
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import android.util.Log
import com.google.adk.kt.memory.InMemoryMemoryService
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AgentChatRunnerCacheTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.d(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun sessionsWithSameConfigurationShareOneRuntimeAndConfirmationReusesIt() = runTest {
        val createdSelections = mutableListOf<ModelSelection?>()
        val runner = AgentChatRunner(
            factory = { spec ->
                createdSelections += spec.selection
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = FakeToolAccessRepository(),
        )
        val selection = ModelSelection("service", "group", "model")

        val execution = runner.createExecution("user", "session-a", selection)
        execution.send("a")
        runner.createExecution("user", "session-a", selection).send("a2")
        runner.createExecution("user", "session-b", selection).send("b")

        // 相同模型 + 访问模式的会话共享同一份 Agent/Runner，只构建一次。
        assertEquals(listOf(selection), createdSelections)

        execution.respondToToolConfirmation("confirmation", true)
        assertEquals(1, createdSelections.size)

        // 新一轮执行仍复用相同的构建产物。
        runner.createExecution("user", "session-a", selection).send("a3")
        assertEquals(1, createdSelections.size)
    }

    @Test
    fun retryUsesAdkRewindBeforeStartingTheNewInvocation() = runTest {
        val sessions = InMemorySessionService()
        val key = SessionKey(AgentChatRunner.APP_NAME, "user", "session")
        val session = sessions.createSession(key)
        sessions.appendEvent(
            session,
            Event(
                invocationId = "attempt-1",
                author = "agent",
                actions = EventActions(stateDelta = mutableMapOf<String, Any>("result" to "partial")),
            ),
        )
        val runner = AgentChatRunner(
            factory = { runtime() },
            sessionService = sessions,
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = FakeToolAccessRepository(),
        )

        runner.createExecution(
            userId = "user",
            sessionId = "session",
            selection = ModelSelection("service", "group", "model"),
        ).send(
            text = "retry",
            rewindBeforeInvocationId = "attempt-1",
        )

        val rewound = sessions.getSession(key)!!
        assertNull(rewound.state["result"])
    }

    @Test
    fun modelOrConfigurationRevisionChangeCreatesNewSharedRuntime() = runTest {
        var revision = 0
        var creations = 0
        val runner = AgentChatRunner(
            factory = { spec ->
                creations += 1
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = FakeToolAccessRepository(),
            configuration = {
                AgentBuildConfigurationSnapshot(
                    revision = revision,
                    pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()),
                )
            },
        )
        val first = ModelSelection("service", "group", "first")
        val second = ModelSelection("service", "group", "second")

        runner.createExecution("user", "session-a", first).send("a")
        runner.createExecution("user", "session-b", first).send("b")
        runner.createExecution("user", "session-a", second).send("a2")
        // first 配置两个会话共享（1 次），切换到 second 新建（共 2 次）。
        assertEquals(2, creations)

        revision += 1
        runner.createExecution("user", "session-b", first).send("b2")
        assertEquals(3, creations)
    }

    @Test
    fun onePluginSnapshotIsSharedByRuntimeFactoryAndAdkPlugins() = runTest {
        val plugin = FakeAgentPlugin("shared")
        val snapshot: PluginRuntimeSnapshot<AgentPlugin> =
            PluginRuntimeSnapshot(7L, listOf(plugin))
        var factorySnapshot: PluginRuntimeSnapshot<*>? = null
        var pluginSnapshot: PluginRuntimeSnapshot<*>? = null
        val runner = AgentChatRunner(
            factory = { spec ->
                factorySnapshot = spec.pluginRuntime
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = FakeToolAccessRepository(),
            configuration = {
                AgentBuildConfigurationSnapshot(
                    revision = listOf(7L),
                    pluginRuntime = snapshot,
                )
            },
            plugins = { runtimeSnapshot ->
                pluginSnapshot = runtimeSnapshot
                emptyList()
            },
        )

        runner.createExecution("user", "session", ModelSelection("service", "group", "model")).send("message")

        assertSame(snapshot, factorySnapshot)
        assertSame(snapshot, pluginSnapshot)
    }

    @Test
    fun runtimeCacheEvictsLeastRecentlyUsedConfiguration() = runTest {
        var creations = 0
        val runner = AgentChatRunner(
            factory = { spec ->
                creations += 1
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = FakeToolAccessRepository(),
        )

        fun selection(index: Int) = ModelSelection("service", "group", "model-$index")

        repeat(AgentChatRunner.MAX_CACHED_RUNTIMES + 1) { index ->
            runner.createExecution("user", "session-$index", selection(index)).send("message")
        }
        runner.createExecution("user", "session-0", selection(0)).send("again")

        assertEquals(AgentChatRunner.MAX_CACHED_RUNTIMES + 2, creations)
    }

    @Test
    fun conversationToolSelectionChangeDoesNotRebuildRuntime() = runTest {
        var creations = 0
        val runner = AgentChatRunner(
            factory = { spec ->
                creations += 1
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = FakeToolAccessRepository(),
        )
        val selection = ModelSelection("service", "group", "model")
        val githubOnly = ConversationToolConfiguration(enabledMcpServerIds = setOf("github"))
        val githubAndFilesystem = ConversationToolConfiguration(
            enabledMcpServerIds = setOf("github", "filesystem"),
        )

        runner.createExecution("user", "session-a", selection, toolConfiguration = githubOnly).send("a")
        runner.createExecution("user", "session-b", selection, toolConfiguration = githubOnly).send("b")
        // 会话内工具勾选变化经 RunConfig metadata 透传，不触发 Agent 重建。
        runner.createExecution("user", "session-a", selection, toolConfiguration = githubAndFilesystem).send("a2")

        assertEquals(1, creations)
    }

    @Test
    fun toolAccessModeChangeCreatesNewSharedRuntime() = runTest {
        var creations = 0
        val toolAccess = FakeToolAccessRepository()
        val runner = AgentChatRunner(
            factory = { spec ->
                creations += 1
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = toolAccess,
        )
        val selection = ModelSelection("service", "group", "model")

        runner.createExecution(
            "user", "session-a", selection,
            toolConfiguration = ConversationToolConfiguration(),
        ).send("a")
        runner.createExecution(
            "user", "session-b", selection,
            toolConfiguration = ConversationToolConfiguration(),
        ).send("b")
        toolAccess.setDefaultToolAccessMode(ToolAccessMode.ON_DEMAND)
        runner.createExecution(
            "user", "session-a", selection,
            toolConfiguration = ConversationToolConfiguration(),
        ).send("a2")

        assertEquals(2, creations)
    }

    @Test
    fun confirmationToolsToggleDoesNotRebuildRuntime() = runTest {
        var creations = 0
        val runner = AgentChatRunner(
            factory = { spec ->
                creations += 1
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = FakeToolAccessRepository(),
        )
        val selection = ModelSelection("service", "group", "model")

        runner.createExecution("user", "session-a", selection, allowConfirmationRequiredTools = true).send("a")
        // 确认工具开关经 RunConfig metadata 透传，不参与缓存键，不触发重建。
        runner.createExecution("user", "session-a", selection, allowConfirmationRequiredTools = false).send("a2")
        runner.createExecution("user", "session-b", selection, allowConfirmationRequiredTools = false).send("b")

        assertEquals(1, creations)
    }

    @Test
    fun pendingExecutionSurvivesCacheEvictionAndConfigurationChanges() = runTest {
        var revision = 0
        var configurationReads = 0
        var creations = 0
        val runner = AgentChatRunner(
            factory = { spec ->
                creations++
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
            toolAccessRepository = FakeToolAccessRepository(),
            configuration = {
                configurationReads++
                AgentBuildConfigurationSnapshot(revision, PluginRuntimeSnapshot(0L, emptyList()))
            },
        )
        val pending = runner.createExecution("user", "waiting", ModelSelection("svc", "group", "original"))
        pending.send("start")
        // 不同配置填满缓存，且同一 sessionId 也可以产生下一轮独立的上下文。
        repeat(AgentChatRunner.MAX_CACHED_RUNTIMES + 1) {
            revision++
            runner.createExecution("user", "waiting", ModelSelection("svc", "group", "new-$it"))
        }
        val readsBeforeResume = configurationReads
        val creationsBeforeResume = creations

        pending.respondToToolConfirmation("confirmation", true)
        pending.respondToInputRequest("input", "get_user_choice", mapOf("value" to "yes"))

        assertEquals(readsBeforeResume, configurationReads)
        assertEquals(creationsBeforeResume, creations)
    }

    private fun runtime(selection: ModelSelection? = null) = AgentRuntime(
        agent = mockk<LlmAgent>(relaxed = true),
        modelRuntime = ModelRuntimeMetadata(
            serviceId = selection?.serviceId ?: "service",
            baseType = ApiProtocol.Standard,
            modelId = selection?.modelId ?: "model",
            fullBaseUrl = "https://example.com",
        ),
    )

    private class FakeToolAccessRepository(
        initial: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    ) : ToolAccessRepository {
        private val mutable = MutableStateFlow(initial)
        override val defaultToolAccessMode: StateFlow<ToolAccessMode> = mutable

        override fun setDefaultToolAccessMode(mode: ToolAccessMode) {
            mutable.value = mode
        }
    }
}
