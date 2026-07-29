package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.asssistantai.agent.tools.dynamic.LocalCategorySource
import github.ponyhuang.asssistantai.agent.tools.dynamic.McpServerSource
import github.ponyhuang.asssistantai.agent.tools.dynamic.OfficialToolCandidateSource
import github.ponyhuang.asssistantai.agent.tools.dynamic.ToolSearchToolset
import github.ponyhuang.asssistantai.agent.tools.mcp.ConversationMcpToolset
import github.ponyhuang.asssistantai.agent.tools.official.DynamicOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.agent.tools.system.LocalToolset
import github.ponyhuang.asssistantai.agent.tools.system.ToolsetConfirmationResumeTool
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.toolauthorization.model.LocalToolCategory
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Agent 工厂 — 按工具访问模式构建三种独立的 [BaseAgent]。
 *
 * 构建期只绑定服务级能力（模型、候选工具来源）；会话级工具勾选
 * （本地工具 / MCP server / 官方函数）通过 `RunConfig.customMetadata` 透传，
 * 由各 Toolset 在每次模型请求时自行过滤 —— 因此会话内修改工具选择
 * 不会触发 Agent 重建。
 *
 * 三种访问模式（[ToolAccessMode]）：
 * - [ToolAccessMode.ALWAYS_AVAILABLE]：全部启用工具直接声明，无检索网关。
 *   本地工具仍然按统一 `localToolset` 暴露，但 [LocalCatalog.toolsByCategory]
 *   使得日后按类裁剪成为可能。
 * - [ToolAccessMode.ON_DEMAND]：只声明核心工具 + `tool_search`，每个业务类别
 *   （[LocalToolCategory]）注册为一个独立检索 source。
 * - [ToolAccessMode.AUTO]：与 `ON_DEMAND` 共享同一组 source，唯一区别是
 *   [ToolSearchToolset] 在预算可承受时直接吐出，否则退化为按需检索。
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
) {
    /**
     * 按访问模式构建 [BaseAgent]。
     *
     * @param selection 模型选择；为 null 时使用默认模型
     * @param toolAccessMode 工具声明加载模式
     */
    suspend fun create(
        selection: ModelSelection? = null,
        toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    ): BaseAgent = when (toolAccessMode) {
        ToolAccessMode.ALWAYS_AVAILABLE -> createAlwaysAvailableAgent(selection)
        ToolAccessMode.ON_DEMAND -> createSearchAgent(selection, ToolAccessMode.ON_DEMAND)
        ToolAccessMode.AUTO -> createSearchAgent(selection, ToolAccessMode.AUTO)
    }

    /** 全量直出：所有启用工具从首个请求起直接声明。 */
    private fun createAlwaysAvailableAgent(selection: ModelSelection?): BaseAgent {
        val (model, _) = createModel(selection)
        return baseAgent(
            model = model,
            dynamicToolSearchEnabled = false,
            toolsets = buildList {
                add(localToolset)
                add(conversationMcpToolset)
                addAll(officialToolsets)
                add(SkillToolset(skillSource))
            },
        )
    }

    /**
     * 检索网关：核心工具（厂商原生官方工具 + 技能）固定声明。
     *
     * 业务工具拆分为多个 source 后注册到 [ToolSearchToolset]：
     * - 每个 [LocalToolCategory] 一个 source，模型命中后 `source` 字段就是类别
     *   中文标签（"音频" / "日历" / "时钟" ...）；
     * - 每个 MCP server 单独一个 source（基于当前 [McpToolsetRegistry] 的解析结果），
     *   单个 server 发现失败时不影响其它 server；
     * - 可展开官方函数（[DynamicOfficialToolset]）继续作为独立 source。
     */
    private suspend fun createSearchAgent(
        selection: ModelSelection?,
        mode: ToolAccessMode,
    ): BaseAgent {
        val (model, modelConfig) = createModel(selection)
        val (dynamicOfficialToolsets, directOfficialToolsets) =
            officialToolsets.partition { it is DynamicOfficialToolset }
        val sources = buildList {
            LocalToolCategory.entries.forEach { category ->
                add(LocalCategorySource(localToolset, category))
            }
            mcpToolsetRegistry.resolve().handles.forEach { handle ->
                add(McpServerSource(handle))
            }
            dynamicOfficialToolsets
                .filterIsInstance<DynamicOfficialToolset>()
                .forEach { toolset ->
                    add(OfficialToolCandidateSource(toolset, modelConfig))
                }
        }
        return baseAgent(
            model = model,
            dynamicToolSearchEnabled = true,
            toolsets = buildList {
                addAll(directOfficialToolsets)
                add(ToolSearchToolset(mode = mode, sources = sources))
                add(SkillToolset(skillSource))
            },
        )
    }

    private fun createModel(selection: ModelSelection?): Pair<Model, ModelConfig> {
        val modelConfig = agentLLMModelFactory.selectModelConfig(selection)
        return agentLLMModelFactory.createModel(modelConfig) to modelConfig
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
