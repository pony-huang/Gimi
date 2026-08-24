package github.ponyhuang.gimi.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.agent.tools.search.TOOL_SEARCH_NAME
import github.ponyhuang.gimi.agent.tools.search.ToolSearchToolset
import github.ponyhuang.gimi.agent.tools.search.ToolVectorSearch
import github.ponyhuang.gimi.agent.tools.mcp.ConversationMcpToolset
import github.ponyhuang.gimi.agent.tools.mcp.McpAuthorizationTool
import github.ponyhuang.gimi.agent.tools.mcp.McpConfigurationTool
import github.ponyhuang.gimi.agent.tools.official.SearchOfficialToolset
import github.ponyhuang.gimi.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.agent.tools.system.LocalToolset
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两种工具访问模式的 Agent 组装验证。
 *
 * 会话级工具勾选不再经过 AgentFactory（改由 RunConfig metadata 透传 + 各
 * Toolset 自过滤），这里只验证每种模式挂载的 Toolset 组合。
 */
class AgentFactoryToolAccessTest {

    @Test
    fun alwaysAvailableAttachesEveryToolsetDirectlyWithoutSearchGateway() = runTest {
        val native = FakeOfficialToolset("web_search")
        val formula = FakeSearchOfficialToolset("formula_tool")
        val factory = factory(officialToolsets = setOf(native, formula))

        val agent = factory.create(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE).agent as LlmAgent

        assertTrue(native in agent.toolsets)
        assertTrue(formula in agent.toolsets)
        assertTrue(agent.toolsets.none { it is ToolSearchToolset })
        assertTrue(agent.toolsets.any { it is LocalToolset })
        assertTrue(agent.toolsets.any { it is ConversationMcpToolset })
    }

    @Test
    fun onDemandKeepsNativeOfficialDirectAndHidesCandidatesBehindSearch() = runTest {
        val native = FakeOfficialToolset("web_search")
        val formula = FakeSearchOfficialToolset("formula_tool")
        val factory = factory(
            localTools = listOf(declarationTool("clock")),
            officialToolsets = setOf(native, formula),
        )

        val agent = factory.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertTrue(native in agent.toolsets)
        assertFalse(formula in agent.toolsets)
        val search = agent.toolsets.filterIsInstance<ToolSearchToolset>().single()
        assertEquals(listOf(TOOL_SEARCH_NAME), search.getTools().map(BaseTool::name))
    }

    @Test
    fun defaultModeIsAlwaysAvailable() = runTest {
        val factory = factory()

        val agent = factory.create().agent as LlmAgent

        assertTrue(agent.toolsets.none { it is ToolSearchToolset })
    }

    @Test
    fun mcpConfigurationImportAndCredentialUpdateAreDirectlyAvailableInBothAccessModes() = runTest {
        val configurationTool = mockk<McpConfigurationTool>(relaxed = true) {
            every { name } returns McpConfigurationTool.NAME
        }
        val authorizationTool = mockk<McpAuthorizationTool>(relaxed = true) {
            every { name } returns McpAuthorizationTool.NAME
        }
        val factory = factory(
            mcpConfigurationTool = configurationTool,
            mcpAuthorizationTool = authorizationTool,
        )

        val always = factory.create(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE).agent as LlmAgent
        val onDemand = factory.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertTrue(configurationTool in always.tools)
        assertTrue(configurationTool in onDemand.tools)
        assertTrue(authorizationTool in always.tools)
        assertTrue(authorizationTool in onDemand.tools)
    }

    @Test
    fun localToolRemainsResolvableForConfirmationResumeWithoutDuplicateDeclaration() = runTest {
        val factory = factory(
            localTools = listOf(declarationTool("adjust_media_volume")),
            confirmationRequiredToolIds = setOf("adjust_media_volume"),
        )

        val agent = factory.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertEquals(
            listOf(
                "adjust_media_volume",
                McpConfigurationTool.NAME,
                McpAuthorizationTool.NAME,
            ),
            agent.tools.map(BaseTool::name),
        )
        assertEquals(null, agent.tools.first().declaration())
    }

    @Test
    fun localToolWithoutConfirmationIsNotRegisteredAsResumeFallback() = runTest {
        val factory = factory(
            localTools = listOf(declarationTool("get_media_volume")),
        )

        val agent = factory.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertEquals(
            listOf(McpConfigurationTool.NAME, McpAuthorizationTool.NAME),
            agent.tools.map(BaseTool::name),
        )
    }

    private fun factory(
        localTools: List<BaseTool> = emptyList(),
        confirmationRequiredToolIds: Set<String> = emptySet(),
        officialToolsets: Set<OfficialToolset> = emptySet(),
        mcpConfigurationTool: McpConfigurationTool = mockk(relaxed = true) {
            every { name } returns McpConfigurationTool.NAME
        },
        mcpAuthorizationTool: McpAuthorizationTool = mockk(relaxed = true) {
            every { name } returns McpAuthorizationTool.NAME
        },
    ): AgentFactory {
        val localToolCatalog = mockk<LocalToolCatalog>()
        every { localToolCatalog.tools() } returns localTools
        every { localToolCatalog.confirmationRequiredToolIds } returns confirmationRequiredToolIds
        val localToolset = mockk<LocalToolset>(relaxed = true)
        coEvery { localToolset.getTools(any()) } returns localTools
        val mcpToolset = mockk<ConversationMcpToolset>(relaxed = true)
        val mcpRegistry = mockk<github.ponyhuang.gimi.agent.McpToolsetRegistry>(relaxed = true)
        coEvery { mcpRegistry.resolve() } returns github.ponyhuang.gimi.agent.McpToolsetResolution(emptyList())
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
            toolVectorSearch = mockk<ToolVectorSearch>(relaxed = true),
            mcpConfigurationTool = mcpConfigurationTool,
            mcpAuthorizationTool = mcpAuthorizationTool,
        )
    }

    private fun modelConfig() = ModelConfig(
        serviceId = SERVICE_ID,
        baseType = ApiProtocol.Standard,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
    )

    private class FakeOfficialToolset(
        private val toolName: String,
    ) : OfficialToolset {
        override suspend fun resolveTools(
            config: ModelRuntimeMetadata,
            selection: ConversationToolConfiguration?,
        ): List<BaseTool> = listOf(declarationTool(toolName))
    }

    private class FakeSearchOfficialToolset(
        private val toolName: String,
    ) : SearchOfficialToolset {
        override val sourceId: String = "formula"
        override val sourceDisplayName: String = "Formula"

        override suspend fun resolveTools(
            config: ModelRuntimeMetadata,
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
                    args: Map<String, Any?>,
                ): Any = emptyMap<String, Any>()
            }
    }
}
