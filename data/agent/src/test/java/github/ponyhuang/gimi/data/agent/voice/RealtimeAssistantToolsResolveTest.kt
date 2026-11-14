@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentContributionRegistry
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 实时助手工具桥的解析边界：模型运行元数据按 Realtime 选择直查服务目录
 * （不走聊天模型过滤）；任何单一来源或目录解析失败都不得抹掉整个工具面，
 * 否则助手模式会退化成「模型自称没有工具」。
 */
class RealtimeAssistantToolsResolveTest {

    private val realtimeSelection = ModelSelection(
        instanceId = "qwen-speech",
        connectionId = "qwen-audio-realtime",
        modelId = "qwen-audio-3.1-realtime-plus",
    )

    /** 只含一个 Realtime 模型的语音服务目录；[instances] 可模拟目录未就绪/服务被移除。 */
    private fun configSource(
        instances: List<ModelServiceInstance> = listOf(speechInstance()),
    ): Pair<AgentModelConfigurationSource, ModelCatalogRepository> {
        val source = mockk<AgentModelConfigurationSource> {
            coEvery { awaitReady() } returns Unit
            every { currentInstances() } returns instances
        }
        val catalog = mockk<ModelCatalogRepository> {
            // 与运行时仓库一致:按选择在实例快照中解析连接视图。
            every { resolveConnection(any()) } answers { instances.resolveConnection(firstArg()) }
        }
        return source to catalog
    }

    /** 语音服务实例:模板身份为厂商(qwen),Realtime 连接携带唯一的实时模型。 */
    private fun speechInstance(): ModelServiceInstance = ModelServiceInstance(
        id = "qwen-speech",
        name = "qwen-speech",
        templateId = "qwen",
        isEnabled = true,
        isOfficialToolsEnabled = false,
        sharedApiKey = "test-key",
        connections = listOf(
            ServiceConnection(
                id = "qwen-audio-realtime",
                capability = ServiceCapability.Realtime,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "wss://dashscope.example/realtime",
                models = listOf(Model(
                    id = "qwen-audio-3.1-realtime-plus",
                    name = "qwen-audio-3.1-realtime-plus",
                    capabilities = setOf(ModelCapability.Realtime),
                    multimodal = MultimodalCapabilities(),
                )),
            ),
        ),
    )

    private fun emptyPluginRuntime() = object : PluginRuntimeProvider<AgentPlugin> {
        override val runtime = MutableStateFlow(PluginRuntimeSnapshot<AgentPlugin>(revision = 0, enabledPlugins = emptyList()))
    }

    private fun tools(
        contributions: AgentContributionRegistry = AgentContributionRegistry(emptySet()),
        config: Pair<AgentModelConfigurationSource, ModelCatalogRepository> = configSource(),
    ): RealtimeAssistantTools = RealtimeAssistantTools(
        contributions = contributions,
        modelServices = config.first,
        modelCatalog = config.second,
        pluginRuntimeProvider = emptyPluginRuntime(),
    )

    /** 一个可下发声明的最小工具，模拟默认全量启用的本地系统工具。 */
    private fun localTool(name: String = "good_tool") = object : BaseTool(name, "test tool") {
        override fun declaration(): FunctionDeclaration? = FunctionDeclaration(name = name, description = description)
        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any = "ok"
    }

    private fun toolset(vararg tools: BaseTool) = object : Toolset {
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = tools.toList()
    }

    /** 模拟 MCP 探测、插件 SDK 等来源在解析时抛错。 */
    private fun failingToolset() = object : Toolset {
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
            throw IllegalStateException("MCP probe failed")
    }

    private fun contribution(sourceId: String, vararg sets: Toolset): AgentContribution = mockk(relaxed = true) {
        every { id } returns sourceId
        every { toolsets(any()) } returns sets.toList()
    }

    @Test
    fun `resolvable realtime selection yields a bridge`() = runTest {
        val bridge = tools().resolve(realtimeSelection)
        assertNotNull(bridge)
        // 空贡献方与空插件列表：桥存在但无声明（空声明面由指令层判空选中性规则）。
        assertTrue(bridge!!.declarations.isEmpty())
    }

    /** 目录查表失败不再让整座工具桥消失；无贡献方时桥存在但声明为空。 */
    @Test
    fun `unresolvable realtime selection no longer drops the tool bridge`() = runTest {
        val removed = tools().resolve(realtimeSelection.copy(modelId = "removed-model"))
        assertNotNull(removed)
        assertTrue(removed!!.declarations.isEmpty())
        val noSelection = tools().resolve(null)
        assertNotNull(noSelection)
        assertTrue(noSelection!!.declarations.isEmpty())
    }

    /** 单个来源解析失败只丢弃该来源，其余工具（含默认本地工具）仍必须下发。 */
    @Test
    fun `a failing toolset does not wipe out the rest of the tool surface`() = runTest {
        val registry = AgentContributionRegistry(
            setOf(
                contribution("local", toolset(localTool())),
                contribution("mcp", failingToolset()),
            ),
        )
        val bridge = tools(registry).resolve(realtimeSelection)
        assertNotNull(bridge)
        assertEquals(listOf("good_tool"), bridge!!.declarations.map { it.name })
    }

    /** 模型目录查表只服务于执行元数据，解析失败不能连带抹掉工具声明。 */
    @Test
    fun `missing model catalog entry still yields the tool declarations`() = runTest {
        val registry = AgentContributionRegistry(setOf(contribution("local", toolset(localTool("clock_now")))))
        val bridge = tools(registry, configSource(instances = emptyList())).resolve(realtimeSelection)
        assertNotNull(bridge)
        assertEquals(listOf("clock_now"), bridge!!.declarations.map { it.name })
    }
}
