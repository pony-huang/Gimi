package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.ThinkingLevel
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.tools.search.ToolSearchToolset
import github.ponyhuang.gimi.data.agent.tools.search.ToolVectorSearch
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
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
 * Agent 工厂 — 按 [AgentBuildSpec] 编排各能力贡献方装配 [BaseAgent]。
 *
 * 本类只负责模型解析与 LlmAgent 组装，工具/工具集全部来自
 * [AgentContributionRegistry] 聚合的 [AgentContribution] 贡献方；新增配置源时
 * 实现贡献方并注册多绑定即可，无需修改本类。
 *
 * 构建期生成 Agent 和不含凭据的 [ModelRuntimeMetadata]；后者与会话级工具勾选
 * 一起进入 invocation RunConfig，各 Toolset 在每次模型请求时自行读取和过滤，
 * 因此会话内修改工具选择不会触发 Agent 重建。
 *
 * 两种访问模式（[ToolAccessMode]）：
 * - [ToolAccessMode.ALWAYS_AVAILABLE]：贡献方的全部常驻工具集直接声明，无检索网关。
 * - [ToolAccessMode.ON_DEMAND]：常驻工具集之外，把贡献方的检索候选源统一注册进
 *   `ToolSearchToolset`，可搜索工具先写入向量索引，命中后再按当前会话开关和
 *   授权状态过滤。
 */
@Singleton
class AgentFactory @Inject constructor(
    private val contributions: AgentContributionRegistry,
    private val agentLLMModelFactory: AgentLLMModelFactory,
    private val toolVectorSearch: ToolVectorSearch,
) {
    /** 按 [spec] 装配 [AgentRuntime]。 */
    suspend fun create(spec: AgentBuildSpec): AgentRuntime {
        val modelConfig = agentLLMModelFactory.selectModelConfig(spec.selection)
        val model = agentLLMModelFactory.createModel(modelConfig)
        val agent = when (spec.toolAccessMode) {
            ToolAccessMode.ALWAYS_AVAILABLE ->
                baseAgent(model, spec, contributions.toolsets(spec))
            ToolAccessMode.ON_DEMAND ->
                baseAgent(
                    model = model,
                    spec = spec,
                    toolsets = contributions.toolsets(spec) + ToolSearchToolset(
                        sources = contributions.candidateSources(spec),
                        vectorSearch = toolVectorSearch,
                    ),
                )
        }
        return AgentRuntime(
            agent = agent,
            modelRuntime = modelConfig.toRuntimeMetadata(),
        )
    }

    /** 机械组装：业务工具/工具集全部来自贡献方聚合结果，此处只补指令与推理配置。 */
    private fun baseAgent(
        model: Model,
        spec: AgentBuildSpec,
        toolsets: List<Toolset>,
    ): BaseAgent = LlmAgent(
        name = "Assistant",
        model = model,
        generateContentConfig = GenerateContentConfig(
            thinkingConfig = ThinkingConfig(thinkingLevel = spec.reasoningEffort.toAdkThinkingLevel()),
        ),
        instruction = Instruction(AgentPrompts.defaultAssistantInstruction()),
        tools = contributions.tools(spec),
        toolsets = toolsets,
    )
}

/** 将产品层四档推理强度转换为 ADK 传给模型适配层的统一配置。 */
private fun ReasoningEffort.toAdkThinkingLevel(): ThinkingLevel = when (this) {
    ReasoningEffort.MINIMAL -> ThinkingLevel.MINIMAL
    ReasoningEffort.LOW -> ThinkingLevel.LOW
    ReasoningEffort.MEDIUM -> ThinkingLevel.MEDIUM
    ReasoningEffort.HIGH -> ThinkingLevel.HIGH
}
