package github.ponyhuang.gimi.data.agent.subtasks

import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.data.agent.ModelConfig
import github.ponyhuang.gimi.domain.conversation.model.SubtaskBatch
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 固定模型快照及缓存失效边界，避免子任务实际调用主模型或复用过期快速模型。 */
class SubtaskModelSelectionTest {
    @Test
    fun snapshotsFastModelFallsBackWhenUnavailableAndInvalidatesCacheOnSelectionChange() = runTest {
        val models = mockk<AgentLLMModelFactory>()
        val primary = ModelConfig("primary", ApiProtocol.OpenaiChatcompletions, "chat", "primary-key", "https://fixture.invalid")
        val fast = primary.copy(serviceId = "fast", modelId = "fast", apiKey = "fast-key")
        every { models.selectModelConfig(any()) } returns primary
        every { models.selectFastModelConfig() } returns fast
        val source = mockk<AgentModelConfigurationSource>()
        val fastSelection = MutableStateFlow<ModelSelection?>(null)
        every { source.configurationRevision } returns MutableStateFlow(1L)
        every { source.fastSelection } returns fastSelection
        val executor = mockk<SubtaskBatchExecutor>()
        val selected = mutableListOf<ModelConfig>()
        coEvery { executor.execute(any(), any(), any(), any(), any(), capture(selected), any()) } returns
            SubtaskBatch("batch", "chat", "inv", "call", 0, emptyList())
        val contribution = DelegateTasksContribution(models, source, executor)
        val spec = AgentBuildSpec(pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()))
        val first = contribution.tools(spec).single()
        val revision = contribution.revision()
        fastSelection.value = ModelSelection("other-service", "group", "other-model")
        assertNotEquals(revision, contribution.revision())
        every { models.selectFastModelConfig() } returns null
        val second = contribution.tools(spec).single()
        val context = mockk<ToolContext>(relaxed = true)
        every { context.invocationContext.session.key } returns SessionKey("app", "user", "chat")
        every { context.invocationContext.invocationId } returns "inv"
        every { context.functionCallId } returns "call"
        every { context.runConfig } returns null
        val args = mapOf("tasks" to listOf(mapOf("title" to "分析", "instruction" to "总结")))
        first.run(context, args)
        second.run(context, args)
        assertEquals(listOf(fast, primary), selected)
    }
}
