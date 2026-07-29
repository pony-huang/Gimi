package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.asssistantai.agent.tools.dynamic.TOOL_SEARCH_NAME
import github.ponyhuang.asssistantai.agent.tools.dynamic.ToolSearchToolset
import github.ponyhuang.asssistantai.agent.tools.mcp.ConversationMcpToolset
import github.ponyhuang.asssistantai.agent.tools.official.DynamicOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.agent.tools.system.LocalToolset
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三种工具访问模式的 Agent 组装验证。
 *
 * 会话级工具勾选不再经过 AgentFactory（改由 RunConfig metadata 透传 + 各
 * Toolset 自过滤），这里只验证每种模式挂载的 Toolset 组合。
 */
class AgentFactoryToolAccessTest {

    @Test
    fun alwaysAvailableAttachesEveryToolsetDirectlyWithoutSearchGateway() = runTest {
        val native = FakeOfficialToolset("web_search")
        val formula = FakeDynamicOfficialToolset("formula_tool")
        val factory = factory(officialToolsets = setOf(native, formula))

        val agent = factory.create(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE) as LlmAgent

        assertTrue(native in agent.toolsets)
        assertTrue(formula in agent.toolsets)
        assertTrue(agent.toolsets.none { it is ToolSearchToolset })
        assertTrue(agent.toolsets.any { it is LocalToolset })
        assertTrue(agent.toolsets.any { it is ConversationMcpToolset })
    }

    @Test
    fun onDemandKeepsNativeOfficialDirectAndHidesCandidatesBehindSearch() = runTest {
        val native = FakeOfficialToolset("web_search")
        val formula = FakeDynamicOfficialToolset("formula_tool")
        val factory = factory(
            localTools = listOf(declarationTool("clock")),
            officialToolsets = setOf(native, formula),
        )

        val agent = factory.create(toolAccessMode = ToolAccessMode.ON_DEMAND) as LlmAgent

        assertTrue(native in agent.toolsets)
        assertFalse(formula in agent.toolsets)
        val search = agent.toolsets.filterIsInstance<ToolSearchToolset>().single()
        assertEquals(listOf(TOOL_SEARCH_NAME), search.getTools().map(BaseTool::name))
    }

    @Test
    fun autoExposesSmallCatalogDirectlyWithinBudget() = runTest {
        val clock = declarationTool("clock")
        val factory = factory(
            localTools = listOf(clock),
            // 让 LocalCategorySource 按类别返回各自唯一的工具，便于走"全量直出"路径
            toolsByCategory = mapOf(
                github.ponyhuang.asssistantai.domain.toolauthorization.model.LocalToolCategory.CALENDAR to listOf(clock),
            ),
        )

        val agent = factory.create(toolAccessMode = ToolAccessMode.AUTO) as LlmAgent

        val search = agent.toolsets.filterIsInstance<ToolSearchToolset>().single()
        assertEquals(listOf("clock"), search.getTools().map(BaseTool::name))
    }

    @Test
    fun defaultModeIsAlwaysAvailable() = runTest {
        val factory = factory()

        val agent = factory.create() as LlmAgent

        assertTrue(agent.toolsets.none { it is ToolSearchToolset })
    }

    @Test
    fun localToolRemainsResolvableForConfirmationResumeWithoutDuplicateDeclaration() = runTest {
        val factory = factory(
            localTools = listOf(declarationTool("adjust_media_volume")),
            confirmationRequiredToolIds = setOf("adjust_media_volume"),
        )

        val agent = factory.create(toolAccessMode = ToolAccessMode.ON_DEMAND) as LlmAgent

        assertEquals(listOf("adjust_media_volume"), agent.tools.map(BaseTool::name))
        assertEquals(null, agent.tools.single().declaration())
    }

    @Test
    fun localToolWithoutConfirmationIsNotRegisteredAsResumeFallback() = runTest {
        val factory = factory(
            localTools = listOf(declarationTool("get_media_volume")),
        )

        val agent = factory.create(toolAccessMode = ToolAccessMode.ON_DEMAND) as LlmAgent

        assertTrue(agent.tools.isEmpty())
    }

    private fun factory(
        localTools: List<BaseTool> = emptyList(),
        confirmationRequiredToolIds: Set<String> = emptySet(),
        officialToolsets: Set<OfficialToolset> = emptySet(),
        toolsByCategory: Map<
            github.ponyhuang.asssistantai.domain.toolauthorization.model.LocalToolCategory,
            List<BaseTool>,
            > = emptyMap(),
    ): AgentFactory {
        val localToolCatalog = mockk<LocalToolCatalog>()
        every { localToolCatalog.tools() } returns localTools
        every { localToolCatalog.confirmationRequiredToolIds } returns confirmationRequiredToolIds
        val localToolset = mockk<LocalToolset>(relaxed = true)
        coEvery { localToolset.getTools(any()) } returns localTools
        coEvery { localToolset.getToolsForCategory(any(), any()) } answers {
            val category = it.invocation.args[1] as github.ponyhuang.asssistantai.domain.toolauthorization.model.LocalToolCategory
            toolsByCategory[category].orEmpty()
        }
        val mcpToolset = mockk<ConversationMcpToolset>(relaxed = true)
        val mcpRegistry = mockk<github.ponyhuang.asssistantai.agent.McpToolsetRegistry>(relaxed = true)
        coEvery { mcpRegistry.resolve() } returns github.ponyhuang.asssistantai.agent.McpToolsetResolution(emptyList())
        val modelFactory = mockk<AgentLLMModelFactory>()
        every { modelFactory.selectModelConfig(any()) } returns modelConfig()
        every { modelFactory.createModel(any()) } returns mockk<Model>(relaxed = true)
        return AgentFactory(
            localToolCatalog = localToolCatalog,
            localToolset = localToolset,
            conversationMcpToolset = mcpToolset,
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
    )

    private class FakeOfficialToolset(
        private val toolName: String,
    ) : OfficialToolset {
        override suspend fun resolveTools(
            config: ModelConfig,
            selection: ConversationToolConfiguration?,
        ): List<BaseTool> = listOf(declarationTool(toolName))
    }

    private class FakeDynamicOfficialToolset(
        private val toolName: String,
    ) : DynamicOfficialToolset {
        override val sourceId: String = "formula"
        override val sourceDisplayName: String = "Formula"

        override suspend fun resolveTools(
            config: ModelConfig,
            selection: ConversationToolConfiguration?,
        ): List<BaseTool> = listOf(declarationTool(toolName))
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
