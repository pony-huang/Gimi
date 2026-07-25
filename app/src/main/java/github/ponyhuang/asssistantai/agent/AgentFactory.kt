package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.SkillToolset
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolRegistry
import github.ponyhuang.asssistantai.data.LLMModelSelection
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
    suspend fun create(selection: LLMModelSelection? = null): BaseAgent {
        val modelConfig = agentModelFactory.selectModelConfig(selection)
        val defaultModel = agentModelFactory.createModel(modelConfig)
        val fastModelConfig = agentModelFactory.selectFastModelConfig() ?: modelConfig
        val fastModel = if (fastModelConfig == modelConfig) {
            defaultModel
        } else {
            agentModelFactory.createModel(fastModelConfig)
        }
        val officialTools = officialToolRegistry.resolve(modelConfig)
        val tools: List<BaseTool> = buildList {
            val enabledToolIds = toolAuthorization.enabledToolIds()
            addAll(localToolCatalog.tools().filter { it.name in enabledToolIds })
            addAll(mcpToolRegistry.tools())
            addAll(officialTools.tools)
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
