package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.tools.PreloadMemoryTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.ThinkingLevel
import github.ponyhuang.gimi.data.agent.contribution.LocalToolContribution
import github.ponyhuang.gimi.data.agent.contribution.McpToolContribution
import github.ponyhuang.gimi.data.agent.contribution.BaseToolContribution
import github.ponyhuang.gimi.data.agent.contribution.ModelCatalogContribution
import github.ponyhuang.gimi.data.agent.contribution.OfficialToolContribution
import github.ponyhuang.gimi.data.agent.contribution.PluginToolContribution
import github.ponyhuang.gimi.data.agent.contribution.SkillToolContribution
import github.ponyhuang.gimi.data.agent.tools.search.TOOL_SEARCH_NAME
import github.ponyhuang.gimi.data.agent.tools.search.ToolSearchToolset
import github.ponyhuang.gimi.data.agent.tools.search.ToolVectorSearch
import github.ponyhuang.gimi.data.agent.tools.mcp.ConversationMcpToolset
import github.ponyhuang.gimi.data.agent.tools.mcp.McpAuthorizationTool
import github.ponyhuang.gimi.data.agent.tools.mcp.McpConfigurationTool
import github.ponyhuang.gimi.data.agent.tools.mcp.McpManualConfigurationTool
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolRegistry
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolSpec
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolBinding
import github.ponyhuang.gimi.data.agent.tools.system.LocalToolset
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两种工具访问模式的 Agent 组装验证。
 *
 * 会话级工具勾选不经过 AgentFactory（改由 RunConfig metadata 透传 + 各
 * Toolset 自过滤），这里只验证每种模式挂载的 Toolset/工具组合；AgentFactory
 * 从贡献方注册表聚合，测试用真实贡献方 + Mock 依赖构成注册表。
 */
class AgentFactoryToolAccessTest {

    @Test
    fun appliesTheSessionReasoningEffortToTheAdkThinkingLevel() = runTest {
        val fixture = fixture()

        val agent = fixture.create(reasoningEffort = ReasoningEffort.HIGH).agent as LlmAgent

        assertEquals(
            ThinkingLevel.HIGH,
            agent.generateContentConfig?.thinkingConfig?.thinkingLevel,
        )
    }

    @Test
    fun alwaysAvailableAttachesOfficialToolsetDirectlyWithoutSearchGateway() = runTest {
        val fixture = fixture()

        val agent = fixture.create(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE).agent as LlmAgent

        assertTrue(fixture.officialToolset in agent.toolsets)
        assertTrue(agent.toolsets.none { it is ToolSearchToolset })
        assertTrue(agent.toolsets.any { it is LocalToolset })
        assertTrue(agent.toolsets.any { it is ConversationMcpToolset })
    }

    @Test
    fun onDemandKeepsOfficialToolsetDirectAndHidesCandidatesBehindSearch() = runTest {
        val fixture = fixture(
            localTools = listOf(declarationTool("clock")),
        )

        val agent = fixture.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertTrue(fixture.officialToolset in agent.toolsets)
        // ON_DEMAND 下候选是否真的被跳过由 DefaultOfficialToolsetTest 验证;
        // 这里验证检索网关挂载且默认只暴露 tool_search。
        val search = agent.toolsets.filterIsInstance<ToolSearchToolset>().single()
        assertEquals(listOf(TOOL_SEARCH_NAME), search.getTools().map(BaseTool::name))
    }

    @Test
    fun defaultModeIsAlwaysAvailable() = runTest {
        val fixture = fixture()

        val agent = fixture.create().agent as LlmAgent

        assertTrue(agent.toolsets.none { it is ToolSearchToolset })
    }

    @Test
    fun preloadMemoryRunsInBothAccessModesWithoutModelDeclaration() = runTest {
        val fixture = fixture()

        val always = fixture.create(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE).agent as LlmAgent
        val onDemand = fixture.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertTrue(always.tools.any { it is PreloadMemoryTool })
        assertTrue(onDemand.tools.any { it is PreloadMemoryTool })
        assertEquals(null, always.tools.filterIsInstance<PreloadMemoryTool>().single().declaration())
    }

    @Test
    fun mcpConfigurationAndManualToolsAreDirectlyAvailableInBothAccessModes() = runTest {
        val configurationTool = mockk<McpConfigurationTool>(relaxed = true) {
            every { name } returns McpConfigurationTool.NAME
        }
        val authorizationTool = mockk<McpAuthorizationTool>(relaxed = true) {
            every { name } returns McpAuthorizationTool.NAME
        }
        val manualConfigurationTool = mockk<McpManualConfigurationTool>(relaxed = true) {
            every { name } returns McpManualConfigurationTool.NAME
        }
        val fixture = fixture(
            mcpConfigurationTool = configurationTool,
            mcpAuthorizationTool = authorizationTool,
            mcpManualConfigurationTool = manualConfigurationTool,
        )

        val always = fixture.create(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE).agent as LlmAgent
        val onDemand = fixture.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertTrue(configurationTool in always.tools)
        assertTrue(configurationTool in onDemand.tools)
        assertTrue(authorizationTool in always.tools)
        assertTrue(authorizationTool in onDemand.tools)
        assertTrue(manualConfigurationTool in always.tools)
        assertTrue(manualConfigurationTool in onDemand.tools)
    }

    @Test
    fun localToolRemainsResolvableForConfirmationResumeWithoutDuplicateDeclaration() = runTest {
        val fixture = fixture(
            localTools = listOf(declarationTool("adjust_media_volume")),
            confirmationRequiredToolIds = setOf("adjust_media_volume"),
        )

        val agent = fixture.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        // 基础工具按贡献方 id 排序聚合：base 工具 → local 确认续接工具 → mcp 维护工具。
        assertEquals(
            listOf(
                "preload_memory",
                "load_memory",
                "adk_request_input",
                "get_user_choice",
                "adjust_media_volume",
                McpConfigurationTool.NAME,
                McpAuthorizationTool.NAME,
                McpManualConfigurationTool.NAME,
            ),
            agent.tools.map(BaseTool::name),
        )
        assertEquals(
            null,
            agent.tools.filterIsInstance<PreloadMemoryTool>().single().declaration(),
        )
    }

    @Test
    fun localToolWithoutConfirmationIsNotRegisteredAsResumeFallback() = runTest {
        val fixture = fixture(
            localTools = listOf(declarationTool("get_media_volume")),
        )

        val agent = fixture.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertEquals(
            listOf(
                "preload_memory",
                "load_memory",
                "adk_request_input",
                "get_user_choice",
                McpConfigurationTool.NAME,
                McpAuthorizationTool.NAME,
                McpManualConfigurationTool.NAME,
            ),
            agent.tools.map(BaseTool::name),
        )
    }

    @Test
    fun pluginToolsetsAreAttachedInBothAccessModes() = runTest {
        val pluginToolset = FakePluginToolset("playback_control")
        val fixture = fixture(pluginToolsets = listOf(pluginToolset))

        val always = fixture.create(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE).agent as LlmAgent
        val onDemand = fixture.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertTrue("always-available 模式应挂载插件 Toolset", pluginToolset in always.toolsets)
        assertTrue("on-demand 模式应挂载插件 Toolset", pluginToolset in onDemand.toolsets)
    }

    /** 组装好的工厂与构建用插件快照，提供按默认参数构建 Agent 的便捷入口。 */
    private class Fixture(
        val agentFactory: AgentFactory,
        val pluginRuntime: PluginRuntimeSnapshot<AgentPlugin>,
        val officialToolset: DefaultOfficialToolset,
    ) {
        suspend fun create(
            toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
            reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
        ): AgentRuntime = agentFactory.create(
            AgentBuildSpec(
                toolAccessMode = toolAccessMode,
                reasoningEffort = reasoningEffort,
                pluginRuntime = pluginRuntime,
            ),
        )
    }

    private fun fixture(
        localTools: List<BaseTool> = emptyList(),
        confirmationRequiredToolIds: Set<String> = emptySet(),
        officialRegistry: OfficialToolRegistry = officialRegistry(),
        pluginToolsets: List<Toolset> = emptyList(),
        mcpConfigurationTool: McpConfigurationTool = mockk(relaxed = true) {
            every { name } returns McpConfigurationTool.NAME
        },
        mcpAuthorizationTool: McpAuthorizationTool = mockk(relaxed = true) {
            every { name } returns McpAuthorizationTool.NAME
        },
        mcpManualConfigurationTool: McpManualConfigurationTool = mockk(relaxed = true) {
            every { name } returns McpManualConfigurationTool.NAME
        },
    ): Fixture {
        val localToolCatalog = mockk<LocalToolCatalog>()
        // 显式限定接收者：块内简单名会优先解析到本函数的同名参数而非 mock 属性。
        every { localToolCatalog.tools() } returns localTools
        every { localToolCatalog.confirmationRequiredToolIds } returns confirmationRequiredToolIds
        val localToolset = mockk<LocalToolset>(relaxed = true)
        coEvery { localToolset.getTools(any()) } returns localTools
        val toolAuthorization = mockk<ToolAuthorizationRepository> {
            every { revision } returns MutableStateFlow(0L)
        }
        val mcpToolset = mockk<ConversationMcpToolset>(relaxed = true)
        val mcpRepository = mockk<McpRepository> {
            every { revision } returns MutableStateFlow(0L)
        }
        val mcpRegistry = mockk<McpToolsetRegistry>(relaxed = true)
        coEvery { mcpRegistry.resolveAll() } returns McpToolsetResolution(emptyList())
        val modelServices = mockk<AgentModelConfigurationSource> {
            every { configurationRevision } returns MutableStateFlow(0L)
        }
        val modelFactory = mockk<AgentLLMModelFactory>()
        every { modelFactory.selectModelConfig(any()) } returns modelConfig()
        every { modelFactory.createModel(any()) } returns mockk<Model>(relaxed = true)
        val plugin = FakeAgentPlugin("test", pluginToolsets = pluginToolsets)
        val pluginRuntimeProvider = FakePluginRuntimeProvider(plugins = listOf(plugin))

        val officialToolset = DefaultOfficialToolset(officialRegistry)
        val registry = AgentContributionRegistry(
            setOf(
                LocalToolContribution(localToolCatalog, localToolset, toolAuthorization),
                McpToolContribution(
                    conversationMcpToolset = mcpToolset,
                    mcpToolsetRegistry = mcpRegistry,
                    mcpConfigurationTool = mcpConfigurationTool,
                    mcpAuthorizationTool = mcpAuthorizationTool,
                    mcpManualConfigurationTool = mcpManualConfigurationTool,
                    mcpRepository = mcpRepository,
                ),
                OfficialToolContribution(officialToolset, officialRegistry),
                SkillToolContribution(mockk<SkillSource>(relaxed = true)),
                PluginToolContribution(pluginRuntimeProvider),
                BaseToolContribution(),
                ModelCatalogContribution(modelServices),
            ),
        )
        return Fixture(
            agentFactory = AgentFactory(
                contributions = registry,
                agentLLMModelFactory = modelFactory,
                toolVectorSearch = mockk<ToolVectorSearch>(relaxed = true),
            ),
            pluginRuntime = pluginRuntimeProvider.runtime.value,
            officialToolset = officialToolset,
        )
    }

    private fun modelConfig() = ModelConfig(
        serviceId = SERVICE_ID,
        baseType = ApiProtocol.Standard,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
    )

    /**
     * 注册表 mock:暴露一个检索候选声明(Kimi formulas 形态),驱动 ON_DEMAND
     * 模式的候选源装配路径。
     */
    private fun officialRegistry(): OfficialToolRegistry = mockk {
        every { all } returns listOf(
            OfficialToolSpec(
                toolId = "kimi_formulas",
                serviceId = "kimi",
                protocols = setOf(ApiProtocol.Standard),
                displayName = "Kimi formulas",
                searchCandidate = true,
                binding = OfficialToolBinding.ProviderDeclaration(wireName = "formula_tool"),
            ),
        )
    }

    /** 模拟插件返回的 ADK [Toolset]：忽略请求期上下文，恒定出一个工具。 */
    private class FakePluginToolset(
        private val toolName: String,
    ) : Toolset {
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
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
                    args: Map<String, Any?>,
                ): Any = emptyMap<String, Any>()
            }
    }
}
