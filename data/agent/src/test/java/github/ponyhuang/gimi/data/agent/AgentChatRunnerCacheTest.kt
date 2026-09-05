package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
        )
        val selection = ModelSelection("service", "group", "model")

        runner.send("user", "session-a", selection, "a")
        runner.send("user", "session-a", selection, "a2")
        runner.send("user", "session-b", selection, "b")

        // 相同模型 + 访问模式的会话共享同一份 Agent/Runner，只构建一次。
        assertEquals(listOf(selection), createdSelections)

        runner.respondToToolConfirmation("user", "session-a", "confirmation", true)
        assertEquals(1, createdSelections.size)

        // 释放会话只移除绑定；共享运行时仍可复用，不触发重建。
        runner.releaseSession("session-a")
        runner.send("user", "session-a", selection, "a3")
        assertEquals(1, createdSelections.size)
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
            configuration = {
                AgentBuildConfigurationSnapshot(
                    revision = revision,
                    pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()),
                )
            },
        )
        val first = ModelSelection("service", "group", "first")
        val second = ModelSelection("service", "group", "second")

        runner.send("user", "session-a", first, "a")
        runner.send("user", "session-b", first, "b")
        runner.send("user", "session-a", second, "a2")
        // first 配置两个会话共享（1 次），切换到 second 新建（共 2 次）。
        assertEquals(2, creations)

        revision += 1
        runner.send("user", "session-b", first, "b2")
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

        runner.send("user", "session", ModelSelection("service", "group", "model"), "message")

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
        )

        fun selection(index: Int) = ModelSelection("service", "group", "model-$index")

        repeat(AgentChatRunner.MAX_CACHED_RUNTIMES + 1) { index ->
            runner.send("user", "session-$index", selection(index), text = "message")
        }
        runner.send("user", "session-0", selection(0), text = "again")

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
        )
        val selection = ModelSelection("service", "group", "model")
        val githubOnly = ConversationToolConfiguration(enabledMcpServerIds = setOf("github"))
        val githubAndFilesystem = ConversationToolConfiguration(
            enabledMcpServerIds = setOf("github", "filesystem"),
        )

        runner.send("user", "session-a", selection, "a", toolConfiguration = githubOnly)
        runner.send("user", "session-b", selection, "b", toolConfiguration = githubOnly)
        // 会话内工具勾选变化经 RunConfig metadata 透传，不触发 Agent 重建。
        runner.send("user", "session-a", selection, "a2", toolConfiguration = githubAndFilesystem)

        assertEquals(1, creations)
    }

    @Test
    fun toolAccessModeChangeCreatesNewSharedRuntime() = runTest {
        var creations = 0
        val runner = AgentChatRunner(
            factory = { spec ->
                creations += 1
                runtime(spec.selection)
            },
            sessionService = mockk<SessionService>(relaxed = true),
            artifactService = null,
            memoryService = InMemoryMemoryService(),
        )
        val selection = ModelSelection("service", "group", "model")

        runner.send(
            "user", "session-a", selection, "a",
            toolConfiguration = ConversationToolConfiguration(
                toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
            ),
        )
        runner.send(
            "user", "session-b", selection, "b",
            toolConfiguration = ConversationToolConfiguration(
                toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
            ),
        )
        runner.send(
            "user", "session-a", selection, "a2",
            toolConfiguration = ConversationToolConfiguration(
                toolAccessMode = ToolAccessMode.ON_DEMAND,
            ),
        )

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
        )
        val selection = ModelSelection("service", "group", "model")

        runner.send("user", "session-a", selection, "a", allowConfirmationRequiredTools = true)
        // 确认工具开关经 RunConfig metadata 透传，不参与缓存键，不触发重建。
        runner.send("user", "session-a", selection, "a2", allowConfirmationRequiredTools = false)
        runner.send("user", "session-b", selection, "b", allowConfirmationRequiredTools = false)

        assertEquals(1, creations)
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
}
