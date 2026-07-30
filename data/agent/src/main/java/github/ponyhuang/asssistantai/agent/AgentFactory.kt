package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.asssistantai.agent.tools.dynamic.LocalToolSource
import github.ponyhuang.asssistantai.agent.tools.dynamic.McpServerSource
import github.ponyhuang.asssistantai.agent.tools.dynamic.OfficialToolCandidateSource
import github.ponyhuang.asssistantai.agent.tools.dynamic.ToolSearchToolset
import github.ponyhuang.asssistantai.agent.tools.dynamic.ToolVectorSearch
import github.ponyhuang.asssistantai.agent.tools.mcp.ConversationMcpToolset
import github.ponyhuang.asssistantai.agent.tools.official.DynamicOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.agent.tools.system.LocalToolset
import github.ponyhuang.asssistantai.agent.tools.system.ToolsetConfirmationResumeTool
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次 Agent 构建的产物。
 *
 * @property agent 交给 ADK Runner 执行的 Agent。
 * @property modelRuntime 可安全放入本次 invocation RunConfig 的非敏感模型信息。
 */
data class AgentRuntime(
    val agent: BaseAgent,
    val modelRuntime: ModelRuntimeMetadata,
)

/**
 * Agent 工厂 — 按工具访问模式构建两种独立的 [BaseAgent]。
 *
 * 构建期生成 Agent 和不含凭据的 [ModelRuntimeMetadata]；后者与会话级工具勾选
 * 一起进入 invocation RunConfig，各 Toolset 在每次模型请求时自行读取和过滤，
 * 因此会话内修改工具选择不会触发 Agent 重建。
 *
 * 两种访问模式（[ToolAccessMode]）：
 * - [ToolAccessMode.ALWAYS_AVAILABLE]：全部启用工具直接声明，无检索网关。
 * - [ToolAccessMode.ON_DEMAND]：只声明核心工具 + `tool_search`，可搜索工具先统一
 *   写入向量索引，命中后再按当前会话开关和授权状态过滤。
 */
@Singleton
class AgentFactory @Inject constructor(
    private val localToolCatalog: LocalToolCatalog,
    private val localToolset: LocalToolset,
    private val conversationMcpToolset: ConversationMcpToolset,
    private val mcpToolsetRegistry: McpToolsetRegistry,
    private val skillSource: SkillSource,
    private val agentLLMModelFactory: AgentLLMModelFactory,
    private val officialToolsets: Set<@JvmSuppressWildcards OfficialToolset>,
    private val toolVectorSearch: ToolVectorSearch,
) {
    /**
     * 按访问模式构建 [AgentRuntime]。
     *
     * @param selection 模型选择；为 null 时使用默认模型
     * @param toolAccessMode 工具声明加载模式
     */
    suspend fun create(
        selection: ModelSelection? = null,
        toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    ): AgentRuntime {
        val modelConfig = agentLLMModelFactory.selectModelConfig(selection)
        val model = agentLLMModelFactory.createModel(modelConfig)
        val agent = when (toolAccessMode) {
            ToolAccessMode.ALWAYS_AVAILABLE -> createAlwaysAvailableAgent(model)
            ToolAccessMode.ON_DEMAND -> createSearchAgent(model, ToolAccessMode.ON_DEMAND)
        }
        return AgentRuntime(
            agent = agent,
            modelRuntime = modelConfig.toRuntimeMetadata(),
        )
    }

    /** 全量直出：所有启用工具从首个请求起直接声明。 */
    private fun createAlwaysAvailableAgent(model: Model): BaseAgent =
        baseAgent(
            model = model,
            dynamicToolSearchEnabled = false,
            toolsets = buildList {
                add(localToolset)
                add(conversationMcpToolset)
                addAll(officialToolsets)
                add(SkillToolset(skillSource))
            },
        )

    /**
     * 检索网关：核心工具（厂商原生官方工具 + 技能）固定声明。
     *
     * 业务工具拆分为多个 source 后注册到 [ToolSearchToolset]：
     * - 全部本地工具构成一个扁平 source；
     * - 每个 MCP server 单独一个 source（基于当前 [McpToolsetRegistry] 的解析结果），
     *   单个 server 发现失败时不影响其它 server；
     * - 可展开官方函数（[DynamicOfficialToolset]）继续作为独立 source。
     */
    private suspend fun createSearchAgent(
        model: Model,
        mode: ToolAccessMode,
    ): BaseAgent {
        val (dynamicOfficialToolsets, directOfficialToolsets) =
            officialToolsets.partition { it is DynamicOfficialToolset }
        val sources = buildList {
            add(LocalToolSource(localToolCatalog, localToolset))
            mcpToolsetRegistry.resolveAll().handles.forEach { handle ->
                add(McpServerSource(handle))
            }
            dynamicOfficialToolsets
                .filterIsInstance<DynamicOfficialToolset>()
                .forEach { toolset ->
                    add(OfficialToolCandidateSource(toolset))
                }
        }
        return baseAgent(
            model = model,
            dynamicToolSearchEnabled = true,
            toolsets = buildList {
                addAll(directOfficialToolsets)
                add(
                    ToolSearchToolset(
                        mode = mode,
                        sources = sources,
                        vectorSearch = toolVectorSearch,
                    ),
                )
                add(SkillToolset(skillSource))
            },
        )
    }

    private fun baseAgent(
        model: Model,
        dynamicToolSearchEnabled: Boolean,
        toolsets: List<Toolset>,
    ): BaseAgent = LlmAgent(
        name = "Assistant",
        model = model,
        instruction = Instruction(
            AgentPrompts.defaultAssistantInstruction(
                dynamicToolSearchEnabled = dynamicToolSearchEnabled,
            ),
        ),
        // ADK 0.6.0 的确认恢复处理器只查 agent.tools；隐藏代理不声明 schema，
        // 但可在恢复时按当前请求上下文重新定位 Toolset 中的真实执行实例。
        tools = localToolCatalog.tools()
            .filter { tool -> tool.name in localToolCatalog.confirmationRequiredToolIds }
            .map { tool -> ToolsetConfirmationResumeTool(localToolset, tool) },
        toolsets = toolsets,
    )
}
