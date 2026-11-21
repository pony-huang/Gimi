package github.ponyhuang.gimi.data.agent.chatbot

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.ThinkingLevel
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContributionRegistry
import github.ponyhuang.gimi.data.agent.AgentFactory
import github.ponyhuang.gimi.data.agent.AgentModelRuntime
import github.ponyhuang.gimi.data.agent.AgentPrompts
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolset
import github.ponyhuang.gimi.data.modelaccess.DefaultAgentLLMModelFactory
import github.ponyhuang.gimi.domain.modelaccess.ModelConfig
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.runtime.AgentProfile
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chatbot 形态的 Agent 装配：只挂官方联网工具，配置全部经 Agent 表达。 */
class ChatbotAgentFactoryTest {

    @Test fun `chatbot agent declares the web toolset only`() = runTest {
        val agent = chatbotAgent()
        assertTrue(agent.toolsets.single() is ChatbotOfficialToolset)
        assertTrue(agent.tools.isEmpty())
        assertEquals(AgentPrompts.chatbotInstruction(), (agent.instruction as Instruction.Text).text)
        assertEquals(8192, agent.generateContentConfig?.maxOutputTokens)
    }

    @Test fun `thinking off means no thinking budget`() = runTest {
        val agent = chatbotAgent(thinkingEnabled = false)
        assertEquals(0, agent.generateContentConfig?.thinkingConfig?.thinkingBudget)
    }

    @Test fun `thinking on maps the effort to a thinking level`() = runTest {
        val agent = chatbotAgent(thinkingEnabled = true, reasoningEffort = ReasoningEffort.HIGH)
        assertEquals(ThinkingLevel.HIGH, agent.generateContentConfig?.thinkingConfig?.thinkingLevel)
    }

    private suspend fun chatbotAgent(
        thinkingEnabled: Boolean = true,
        reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    ): LlmAgent = factory().create(
        AgentBuildSpec(
            pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()),
            profile = AgentProfile.CHATBOT,
            thinkingEnabled = thinkingEnabled,
            reasoningEffort = reasoningEffort,
        ),
    ).agent as LlmAgent

    private fun factory(): AgentFactory {
        val modelFactory = mockk<DefaultAgentLLMModelFactory>()
        every { modelFactory.selectModelConfig(any()) } returns ModelConfig(
            serviceId = "deepseek",
            templateId = "deepseek",
            baseType = ApiProtocol.Anthropic,
            modelId = "deepseek-flash",
            apiKey = "key",
            fullBaseUrl = "https://api.deepseek.com/anthropic",
        )
        every { modelFactory.createModel(any(), any()) } returns mockk<Model>(relaxed = true)
        return AgentFactory(
            contributions = AgentContributionRegistry(emptySet()),
            agentLLMModelFactory = AgentModelRuntime(modelFactory),
            toolVectorSearch = mockk(relaxed = true),
            chatbotTools = ChatbotOfficialToolset(mockk<DefaultOfficialToolset>(relaxed = true), catalog),
        )
    }

    /** Chatbot 工具集测试只消费目录声明；解析流程由 DefaultOfficialToolset 自己的测试覆盖。 */
    private val catalog = object : github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog {
        override val all = github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport().all
        override fun availableSpecsFor(
            templateId: String,
            protocol: ApiProtocol,
            modelId: String,
        ) = emptyList<github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec>()

        override fun enabledSearchCandidateSpecs() =
            emptyList<github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec>()

        override fun supportedToolIds(templateId: String, protocol: ApiProtocol) = emptySet<String>()

        override fun availableTools(
            activeInstance: github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance,
            activeModelId: String,
        ) = emptyList<github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolAvailability>()

        override suspend fun listFunctions(toolId: String) =
            emptyList<github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction>()
    }
}
