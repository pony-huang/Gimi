package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.SkillToolset
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
    private val agentModelFactory: AgentModelFactory
) {
    suspend fun create(selection: LLMModelSelection? = null): BaseAgent {
        val defaultModel = agentModelFactory.selectDefaultModel(selection)
        val fastModel = agentModelFactory.selectFastModel(selection)
        val tools: List<BaseTool> = buildList {
            val enabledToolIds = toolAuthorization.enabledToolIds()
            addAll(localToolCatalog.tools().filter { it.name in enabledToolIds })
            addAll(mcpToolRegistry.tools())
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
            toolsets = listOf(SkillToolset(skillSource)),
            beforeModelCallbacks = listOf(titleCallbacks.beforeModel()),
            afterModelCallbacks = listOf(titleCallbacks.afterModel()),
            afterAgentCallbacks = listOf(titleCallbacks.afterAgent()),
        )
    }

}
