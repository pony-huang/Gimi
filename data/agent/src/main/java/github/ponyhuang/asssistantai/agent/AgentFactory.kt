package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.SkillToolset
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Agent 工厂 — 根据模型选择和工具配置构建 [BaseAgent]。
 *
 * 负责：
 * - 通过 [AgentLLMModelFactory] 解析模型配置
 * - 组合本地工具（[LocalToolCatalog]）、MCP 工具（[McpToolsetRegistry]）、
 *   官方工具（[OfficialToolset]）和技能工具集（[SkillSource]）
 * - 按 [ToolAuthorizationRepository] 和 [ConversationToolConfiguration] 过滤启用的工具
 * - 根据 [allowConfirmationRequiredTools] 决定是否排除需要用户确认的工具
 */
@Singleton
class AgentFactory @Inject constructor(
    private val localToolCatalog: LocalToolCatalog,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val mcpToolsetRegistry: McpToolsetRegistry,
    private val skillSource: SkillSource,
    private val agentLLMModelFactory: AgentLLMModelFactory,
    private val officialToolsets: Set<@JvmSuppressWildcards OfficialToolset>,
) {
    /**
     * 构建 [BaseAgent]。
     *
     * @param selection 模型选择；为 null 时使用默认模型
     * @param allowConfirmationRequiredTools 是否允许需要用户确认的工具
     * @param toolConfiguration 会话工具配置，为 null 时按全局授权决定启用哪些工具
     */
    suspend fun create(
        selection: ModelSelection? = null,
        allowConfirmationRequiredTools: Boolean = true,
        toolConfiguration: ConversationToolConfiguration? = null,
    ): BaseAgent {
        val selectedModelConfig = agentLLMModelFactory.selectModelConfig(selection)
        val modelConfig = toolConfiguration?.let(selectedModelConfig::forConversation)
            ?: selectedModelConfig
        val model = agentLLMModelFactory.createModel(modelConfig)
        val mcpResolution = mcpToolsetRegistry.resolve(
            toolConfiguration?.enabledMcpServerIds,
        )
        val configuredTools: List<BaseTool> = buildList {
            val enabledToolIds = toolConfiguration?.enabledLocalToolIds
                ?: toolAuthorization.enabledToolIds()
            addAll(localToolCatalog.tools().filter { it.name in enabledToolIds })
        }
        val tools = if (allowConfirmationRequiredTools) {
            configuredTools
        } else {
            excludeConfirmationRequiredTools(configuredTools)
        }
        return LlmAgent(
            name = "Assistant",
            model = model,
            instruction = Instruction(
                AgentPrompts.defaultAssistantInstruction(
                    tools.mapTo(linkedSetOf(), BaseTool::name) +
                            mcpResolution.toolNames +
                            modelConfig.officialTools,
                ),
            ),
            tools = tools,
            toolsets = officialToolsets.toList() +
                    SkillToolset(skillSource) +
                    mcpResolution.toolsets,
        )
    }

}

/** 从工具列表中排除需要用户确认的 [FunctionTool]。 */
internal fun excludeConfirmationRequiredTools(tools: List<BaseTool>): List<BaseTool> =
    tools.filterNot { tool ->
        if (tool !is FunctionTool) {
            false
        } else {
            runCatching {
                val getter = FunctionTool::class.java
                    .getDeclaredMethod("getRequiresConfirmation")
                    .apply { isAccessible = true }

                @Suppress("UNCHECKED_CAST")
                val requiresConfirmation =
                    getter.invoke(tool) as (Map<String, Any>) -> Boolean
                requiresConfirmation(emptyMap())
            }.getOrDefault(true)
        }
    }
