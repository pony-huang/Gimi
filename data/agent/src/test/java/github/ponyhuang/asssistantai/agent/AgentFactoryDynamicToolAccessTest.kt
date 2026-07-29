package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.asssistantai.agent.tools.dynamic.DynamicToolAccessToolset
import github.ponyhuang.asssistantai.agent.tools.official.DynamicOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentFactoryDynamicToolAccessTest {

    @Test
    fun sessionSelectionCannotReenableGloballyUnauthorizedLocalTools() = runTest {
        val authorized = declarationTool("authorized_tool")
        val disabled = declarationTool("disabled_tool")
        val factory = factory(
            localTools = listOf(authorized, disabled),
            globallyEnabledLocalIds = setOf("authorized_tool"),
        )

        val agent = factory.create(
            toolConfiguration = ConversationToolConfiguration(
                enabledLocalToolIds = setOf("authorized_tool", "disabled_tool"),
                toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
            ),
        ) as LlmAgent
        val dynamic = agent.toolsets.filterIsInstance<DynamicToolAccessToolset>().single()

        assertEquals(
            listOf("authorized_tool"),
            dynamic.getTools().map(BaseTool::name),
        )
    }

    @Test
    fun nativeOfficialToolsetsStayDirectWhileFormulaToolsetsBecomeCandidates() = runTest {
        val native = FakeOfficialToolset("web_search")
        val formula = FakeDynamicOfficialToolset("formula_tool")
        val factory = factory(officialToolsets = setOf(native, formula))

        val agent = factory.create(
            toolConfiguration = ConversationToolConfiguration(
                enabledOfficialFunctionIdsByService = mapOf(
                    SERVICE_ID to mapOf("kimi_formulas" to setOf("formula_tool")),
                ),
                toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
            ),
        ) as LlmAgent

        assertTrue(native in agent.toolsets)
        assertFalse(formula in agent.toolsets)
        val dynamic = agent.toolsets.filterIsInstance<DynamicToolAccessToolset>().single()
        assertEquals(listOf("formula_tool"), dynamic.getTools().map(BaseTool::name))
    }

    @Test
    fun absentConversationConfigurationKeepsLegacyAlwaysAvailableBehavior() = runTest {
        val factory = factory(localTools = listOf(declarationTool("clock")))

        val agent = factory.create(toolConfiguration = null) as LlmAgent
        val dynamic = agent.toolsets.filterIsInstance<DynamicToolAccessToolset>().single()

        assertEquals(listOf("clock"), dynamic.getTools().map(BaseTool::name))
    }

    private fun factory(
        localTools: List<BaseTool> = emptyList(),
        globallyEnabledLocalIds: Set<String> = localTools.mapTo(hashSetOf(), BaseTool::name),
        officialToolsets: Set<OfficialToolset> = emptySet(),
    ): AgentFactory {
        val localCatalog = mockk<LocalToolCatalog>()
        every { localCatalog.tools() } returns localTools
        val authorization = mockk<ToolAuthorizationRepository>()
        every { authorization.enabledToolIds() } returns globallyEnabledLocalIds
        val mcpRegistry = mockk<McpToolsetRegistry>()
        coEvery { mcpRegistry.resolve(any()) } returns McpToolsetResolution(emptyList())
        val modelFactory = mockk<AgentLLMModelFactory>()
        every { modelFactory.selectModelConfig(any()) } returns modelConfig()
        every { modelFactory.createModel(any()) } returns mockk<Model>(relaxed = true)
        return AgentFactory(
            localToolCatalog = localCatalog,
            toolAuthorization = authorization,
            mcpToolsetRegistry = mcpRegistry,
            skillSource = mockk<SkillSource>(relaxed = true),
            agentLLMModelFactory = modelFactory,
            officialToolsets = officialToolsets,
        )
    }

    private fun modelConfig() = ModelConfig(
        serviceId = SERVICE_ID,
        baseType = ApiProtocol.Standard,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
        officialTools = listOf("web_search", "kimi_formulas"),
        enabledOfficialFunctions = mapOf(
            "kimi_formulas" to setOf("formula_tool"),
        ),
    )

    private class FakeOfficialToolset(
        private val toolName: String,
    ) : OfficialToolset {
        override suspend fun resolveTools(config: ModelConfig): List<BaseTool> =
            listOf(declarationTool(toolName))
    }

    private class FakeDynamicOfficialToolset(
        private val toolName: String,
    ) : DynamicOfficialToolset {
        override val sourceId: String = "formula"
        override val sourceDisplayName: String = "Formula"

        override suspend fun resolveTools(config: ModelConfig): List<BaseTool> =
            listOf(declarationTool(toolName))
    }

    private companion object {
        const val SERVICE_ID = "kimi"

        fun declarationTool(name: String): BaseTool =
            object : BaseTool(name, name) {
                override fun declaration(): FunctionDeclaration =
                    FunctionDeclaration(name = name, description = description)

                override suspend fun run(
                    context: ToolContext,
                    args: Map<String, Any>,
                ): Any = emptyMap<String, Any>()
            }
    }
}
