package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.events.EventActions
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.ThinkingLevel
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.data.agent.contribution.BaseToolContribution
import github.ponyhuang.gimi.data.agent.contribution.LocalToolContribution
import github.ponyhuang.gimi.data.agent.contribution.McpToolContribution
import github.ponyhuang.gimi.data.agent.contribution.ModelCatalogContribution
import github.ponyhuang.gimi.data.agent.contribution.OfficialToolContribution
import github.ponyhuang.gimi.data.agent.contribution.PluginToolContribution
import github.ponyhuang.gimi.data.agent.contribution.SkillToolContribution
import github.ponyhuang.gimi.data.agent.tools.mcp.ConversationMcpToolset
import github.ponyhuang.gimi.data.agent.tools.mcp.McpAuthorizationTool
import github.ponyhuang.gimi.data.agent.tools.mcp.McpConfigurationTool
import github.ponyhuang.gimi.data.agent.tools.mcp.McpManualConfigurationTool
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolFactory
import github.ponyhuang.gimi.data.agent.tools.search.TOOL_SEARCH_NAME
import github.ponyhuang.gimi.data.agent.tools.search.ToolSearchToolset
import github.ponyhuang.gimi.data.agent.tools.search.ToolVectorSearch
import github.ponyhuang.gimi.data.agent.tools.search.ToolVectorMatch
import github.ponyhuang.gimi.data.agent.tools.search.ToolVectorDocument
import github.ponyhuang.gimi.data.agent.tools.system.LocalToolset
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolBinding
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
                "load_memory",
                "load_artifacts",
                "adk_request_input",
                "get_user_choice",
                "adjust_media_volume",
                McpConfigurationTool.NAME,
                McpAuthorizationTool.NAME,
                McpManualConfigurationTool.NAME,
            ),
            agent.tools.map(BaseTool::name),
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
                "load_memory",
                "load_artifacts",
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
    fun pluginToolsAreDeclaredDirectlyOnlyInAlwaysAvailableMode() = runTest {
        val pluginToolset = FakePluginToolset("playback_control")
        val pluginTool = declarationTool("plugin_action")
        val fixture = fixture(pluginTools = listOf(pluginTool), pluginToolsets = listOf(pluginToolset))

        val always = fixture.create(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE).agent as LlmAgent
        val onDemand = fixture.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent

        assertTrue("always-available 模式应挂载插件 Toolset", pluginToolset in always.toolsets)
        assertTrue(pluginTool in always.tools)
        assertTrue("按需模式不应直接挂载插件 Toolset", pluginToolset !in onDemand.toolsets)
        assertTrue("按需模式不应直接声明插件工具", pluginTool !in onDemand.tools)
        assertEquals(
            listOf(TOOL_SEARCH_NAME),
            onDemand.toolsets.filterIsInstance<ToolSearchToolset>().single().getTools().map(BaseTool::name),
        )
    }

    @Test
    fun onDemandLoadsTheWholeMatchedPluginToolsetWithItsInstructions() = runTest {
        val groupedNames = listOf("music_search") + (1..9).map { "music_action_$it" }
        val matched = FakePluginToolset(groupedNames.first(), groupedNames.drop(1))
        val unrelated = FakePluginToolset("unrelated_action")
        val vectorSearch = mockk<ToolVectorSearch>()
        coEvery { vectorSearch.search(any(), any(), any(), any()) } answers {
            val documents = secondArg<List<ToolVectorDocument>>()
            listOf(ToolVectorMatch(documents.single { it.text.startsWith("Tool: music_search\n") }.key, 0.0))
        }
        val fixture = fixture(pluginToolsets = listOf(matched, unrelated), vectorSearch = vectorSearch)
        val agent = fixture.create(toolAccessMode = ToolAccessMode.ON_DEMAND).agent as LlmAgent
        val search = agent.toolsets.filterIsInstance<ToolSearchToolset>().single()
        val state = mutableMapOf<String, Any>()
        val readonly = mockk<ReadonlyContext>(relaxed = true) {
            every { this@mockk.state } answers { state }
            every { runConfig } returns null
        }
        coEvery { readonly.getEvents(any()) } returns emptyList()
        val actions = EventActions()
        val context = mockk<ToolContext> {
            every { this@mockk.context } returns readonly
            every { this@mockk.actions } returns actions
        }

        assertEquals(listOf(TOOL_SEARCH_NAME), search.getTools(readonly).map(BaseTool::name))
        search.processLlmRequest(context, LlmRequest())
        assertEquals(0, matched.processedRequests)
        assertEquals(0, unrelated.processedRequests)

        val result = search.search("music", context)
        state.putAll(actions.stateDelta)

        // 超过默认 8 个工具的软预算也必须保持 Toolset 完整，不能丢失登录或执行工具。
        assertEquals(listOf(TOOL_SEARCH_NAME) + groupedNames, search.getTools(readonly).map(BaseTool::name))
        assertEquals(0, result["omitted_match_count"])
        val processed = search.processLlmRequest(context, LlmRequest())
        val instructions = processed.config.systemInstruction!!.parts.mapNotNull(Part::text).joinToString("\n")
        assertTrue(instructions.contains("instructions:music_search"))
        assertTrue(!instructions.contains("instructions:unrelated_action"))
        assertEquals(1, matched.processedRequests)
        assertEquals(0, unrelated.processedRequests)
        val loaded = search.getTools(readonly).single { it.name == "music_action_9" }
        assertEquals(emptyMap<String, Any>(), loaded.run(context, emptyMap()))
    }

    @Test
    fun onDemandCanDiscoverPluginsThatOnlyExposeDirectTools() = runTest {
        val provider = FakePluginRuntimeProvider(listOf(FakeAgentPlugin(
            "direct", pluginTools = listOf(declarationTool("login"), declarationTool("query")),
        )))
        val contribution = PluginToolContribution(provider)
        val sources = contribution.candidateSources(AgentBuildSpec(
            toolAccessMode = ToolAccessMode.ON_DEMAND,
            pluginRuntime = provider.runtime.value,
        ))
        assertEquals(listOf("login", "query"), sources.single().loadEnabledTools(null).map(BaseTool::name))
        assertTrue(sources.single().loadAsGroup)
        assertTrue(contribution.candidateSources(AgentBuildSpec(pluginRuntime = provider.runtime.value)).isEmpty())
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
        officialCatalog: OfficialToolFunctionCatalog = officialCatalog(),
        pluginTools: List<BaseTool> = emptyList(),
        pluginToolsets: List<Toolset> = emptyList(),
        vectorSearch: ToolVectorSearch = mockk(relaxed = true),
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
        val plugin = FakeAgentPlugin("test", pluginTools = pluginTools, pluginToolsets = pluginToolsets)
        val pluginRuntimeProvider = FakePluginRuntimeProvider(plugins = listOf(plugin))

        val officialToolset = DefaultOfficialToolset(officialCatalog, mockk<OfficialToolFactory>(), FakeToolAccessRepository())
        val registry = AgentContributionRegistry(
            setOf(
                LocalToolContribution(localToolCatalog, localToolset, mockk(relaxed = true), toolAuthorization),
                McpToolContribution(
                    conversationMcpToolset = mcpToolset,
                    mcpToolsetRegistry = mcpRegistry,
                    mcpConfigurationTool = mcpConfigurationTool,
                    mcpAuthorizationTool = mcpAuthorizationTool,
                    mcpManualConfigurationTool = mcpManualConfigurationTool,
                    mcpRepository = mcpRepository,
                ),
                OfficialToolContribution(officialToolset, officialCatalog),
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
                toolVectorSearch = vectorSearch,
            ),
            pluginRuntime = pluginRuntimeProvider.runtime.value,
            officialToolset = officialToolset,
        )
    }

    private fun modelConfig() = ModelConfig(
        serviceId = SERVICE_ID,
        baseType = ApiProtocol.OpenaiChatcompletions,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
    )

    /**
     * 目录 mock:暴露一个检索候选声明(Kimi web tools 形态),驱动 ON_DEMAND
     * 模式的候选源装配路径。
     */
    private fun officialCatalog(): OfficialToolFunctionCatalog = mockk {
        every { enabledSearchCandidateSpecs() } returns emptyList()
        every { all } returns listOf(
            OfficialToolSpec(
                toolId = "kimi_web_tools",
                serviceId = "kimi",
                protocols = setOf(ApiProtocol.OpenaiChatcompletions),
                displayName = "Kimi web tools",
                searchCandidate = true,
                binding = OfficialToolBinding.ProviderDeclaration("kimi_search_pro"),
            ),
        )
    }

    /** 模拟插件返回的 ADK [Toolset]：忽略请求期上下文，恒定出一个工具。 */
    private class FakePluginToolset(
        private val toolName: String,
        private val additionalNames: List<String> = emptyList(),
    ) : Toolset {
        var processedRequests: Int = 0

        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
            (listOf(toolName) + additionalNames).map(::declarationTool)

        override suspend fun processLlmRequest(toolContext: ToolContext, llmRequest: LlmRequest): LlmRequest {
            processedRequests++
            return llmRequest.appendInstructions(Content(parts = listOf(Part(text = "instructions:$toolName"))))
        }
    }

    private class FakeToolAccessRepository(
        initial: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    ) : ToolAccessRepository {
        private val mutable = MutableStateFlow(initial)
        override val defaultToolAccessMode: StateFlow<ToolAccessMode> = mutable

        override fun setDefaultToolAccessMode(mode: ToolAccessMode) {
            mutable.value = mode
        }
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
