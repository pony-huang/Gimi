package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.ThinkingLevel
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.chatbot.ChatbotOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.search.ToolSearchToolset
import github.ponyhuang.gimi.data.agent.tools.search.ToolVectorSearch
import github.ponyhuang.gimi.data.agent.a2a.RemoteAgentFailureRecovery
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.runtime.AgentProfile
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import javax.inject.Inject
import javax.inject.Singleton
import github.ponyhuang.gimi.domain.modelaccess.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelaccess.toRuntimeMetadata

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
    private val agentLLMModelFactory: AgentModelRuntime,
    private val toolVectorSearch: ToolVectorSearch,
    private val chatbotTools: ChatbotOfficialToolset,
) {
    /** 按 [spec] 装配 [AgentRuntime]。 */
    suspend fun create(spec: AgentBuildSpec): AgentRuntime {
        val modelConfig = agentLLMModelFactory.selectModelConfig(spec.selection)
        val model = agentLLMModelFactory.createModel(
            modelConfig,
            // Chatbot 形态用厂商随回答返回的会话标题，省掉标题插件的独立快模型请求。
            conversationTitleEnabled = spec.profile == AgentProfile.CHATBOT,
        )
        val agent = when (spec.profile) {
            AgentProfile.ASSISTANT -> assistantAgent(model, spec)
            AgentProfile.CHATBOT -> chatbotAgent(model, spec)
        }
        return AgentRuntime(
            agent = agent,
            modelRuntime = modelConfig.toRuntimeMetadata(),
        )
    }

    /** 助手形态：能力贡献方聚合的全部工具集、工具与子 agent 都挂载。 */
    private suspend fun assistantAgent(model: Model, spec: AgentBuildSpec): BaseAgent {
        val subAgents = contributions.subAgents(spec)
        return when (spec.toolAccessMode) {
            ToolAccessMode.ALWAYS_AVAILABLE ->
                baseAgent(model, spec, contributions.toolsets(spec), subAgents)
            ToolAccessMode.ON_DEMAND ->
                baseAgent(
                    model = model,
                    spec = spec,
                    toolsets = contributions.toolsets(spec) + ToolSearchToolset(
                        sources = contributions.candidateSources(spec),
                        vectorSearch = toolVectorSearch,
                    ),
                    subAgents = subAgents,
                )
        }
    }

    /**
     * Chatbot 形态：只挂官方联网工具，不声明任何设备侧工具。
     *
     * 工具集在请求期按会话开关过滤，思考配置与助手形态同源，
     * 指令换成 Chatbot 专属提示词，输出上限按 Chatbot 场景单独约定。
     */
    private fun chatbotAgent(model: Model, spec: AgentBuildSpec): BaseAgent = LlmAgent(
        name = AgentSessionIdentity.CHATBOT_NAME,
        model = model,
        generateContentConfig = GenerateContentConfig(
            maxOutputTokens = CHATBOT_MAX_OUTPUT_TOKENS,
            thinkingConfig = if (spec.thinkingEnabled) {
                ThinkingConfig(thinkingLevel = spec.reasoningEffort.toAdkThinkingLevel())
            } else {
                ThinkingConfig(includeThoughts = false, thinkingBudget = 0)
            },
        ),
        instruction = Instruction(AgentPrompts.chatbotInstruction(spec.instructionSuffix)),
        toolsets = listOf(chatbotTools),
    )

    /** 机械组装：业务工具/工具集与 sub-agent 全部来自贡献方聚合结果，此处只补指令与推理配置。 */
    private fun baseAgent(
        model: Model,
        spec: AgentBuildSpec,
        toolsets: List<Toolset>,
        subAgents: List<BaseAgent>,
    ): BaseAgent = LlmAgent(
        name = github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity.ASSISTANT_NAME,
        model = model,
        generateContentConfig = GenerateContentConfig(
            thinkingConfig = if (spec.thinkingEnabled) {
                ThinkingConfig(thinkingLevel = spec.reasoningEffort.toAdkThinkingLevel())
            } else {
                ThinkingConfig(includeThoughts = false, thinkingBudget = 0)
            },
        ),
        instruction = Instruction(AgentPrompts.defaultAssistantInstruction(voiceResponse = spec.voiceResponse) + spec.instructionSuffix),
        tools = contributions.tools(spec),
        toolsets = toolsets,
        subAgents = subAgents,
        beforeModelCallbacks = listOf(RemoteAgentFailureRecovery.beforeModel),
        beforeToolCallbacks = listOf(RemoteAgentFailureRecovery.beforeTool),
    )

    private companion object {
        /** Chatbot 形态的输出上限；比助手形态宽松，检索类回答可能较长。 */
        const val CHATBOT_MAX_OUTPUT_TOKENS: Int = 8192
    }
}

/** 将产品层四档推理强度转换为 ADK 传给模型适配层的统一配置。 */
private fun ReasoningEffort.toAdkThinkingLevel(): ThinkingLevel = when (this) {
    ReasoningEffort.MINIMAL -> ThinkingLevel.MINIMAL
    ReasoningEffort.LOW -> ThinkingLevel.LOW
    ReasoningEffort.MEDIUM -> ThinkingLevel.MEDIUM
    ReasoningEffort.HIGH -> ThinkingLevel.HIGH
}
