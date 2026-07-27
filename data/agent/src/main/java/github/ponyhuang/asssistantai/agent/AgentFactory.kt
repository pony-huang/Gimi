package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.SkillToolset
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolRegistry
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AgentFactory @Inject constructor(
    private val localToolCatalog: LocalToolCatalog,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val mcpToolRegistry: McpToolRegistry,
    private val skillSource: SkillSource,
    private val agentModelFactory: AgentModelFactory,
    private val officialToolRegistry: OfficialToolRegistry,
) {
    suspend fun create(
        selection: LLMModelSelection? = null,
        allowConfirmationRequiredTools: Boolean = true,
        toolConfiguration: ConversationToolConfiguration? = null,
    ): BaseAgent {
        val selectedModelConfig = agentModelFactory.selectModelConfig(selection)
        val modelConfig = toolConfiguration?.let(selectedModelConfig::forConversation)
            ?: selectedModelConfig
        val defaultModel = agentModelFactory.createModel(modelConfig)
        val fastModelConfig = agentModelFactory.selectFastModelConfig() ?: modelConfig
        val fastModel = if (fastModelConfig == modelConfig) {
            defaultModel
        } else {
            agentModelFactory.createModel(fastModelConfig)
        }
        val officialTools = officialToolRegistry.resolve(modelConfig)
        val configuredTools: List<BaseTool> = buildList {
            val enabledToolIds = toolConfiguration?.enabledLocalToolIds
                ?: toolAuthorization.enabledToolIds()
            addAll(localToolCatalog.tools().filter { it.name in enabledToolIds })
            addAll(mcpToolRegistry.tools(toolConfiguration?.enabledMcpServerIds))
            addAll(officialTools.tools)
        }
        val tools = if (allowConfirmationRequiredTools) {
            configuredTools
        } else {
            excludeConfirmationRequiredTools(configuredTools)
        }
        val titleCallbacks = ConversationTitleCallbacks(fastModel)
        return LlmAgent(
            name = "Assistant",
            model = defaultModel,
            instruction = Instruction(
                AgentPrompts.defaultAssistantInstruction(
                    tools.mapTo(
                        linkedSetOf(),
                        BaseTool::name
                    )
                ),
            ),
            tools = tools,
            toolsets = officialTools.toolsets + SkillToolset(skillSource),
            beforeModelCallbacks = listOf(titleCallbacks.beforeModel()),
            afterModelCallbacks = listOf(titleCallbacks.afterModel()),
            afterAgentCallbacks = listOf(titleCallbacks.afterAgent()),
        )
    }

}

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
