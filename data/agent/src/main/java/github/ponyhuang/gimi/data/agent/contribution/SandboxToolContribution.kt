package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.tools.sandbox.SandboxTools
import github.ponyhuang.gimi.data.agent.tools.sandbox.SandboxEnvironment
import github.ponyhuang.gimi.data.agent.tools.skills.InstallSkillFromSandboxTool
import com.google.adk.kt.tools.environment.EnvironmentToolset
import com.google.adk.kt.annotations.ExperimentalEnvironmentApi
import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolsetCandidateSource
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** E2B 工具贡献方，配置 revision 使 Agent 缓存失效，两种加载模式使用同一执行门禁。 */
@Singleton
@OptIn(ExperimentalEnvironmentApi::class)
class SandboxToolContribution @Inject constructor(
    private val tools: SandboxTools,
    private val settings: SandboxSettingsRepository,
    environment: SandboxEnvironment,
    private val installSkill: InstallSkillFromSandboxTool,
) : AgentContribution {
    private val sandboxTools = object : Toolset {
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
            if (settings.configuration.value.isReady) tools.getTools(readonlyContext) + installSkill else emptyList()
        override fun close() = tools.close()
    }
    private val environmentTools = object : Toolset {
        private val delegate = EnvironmentToolset(environment)
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
            if (settings.configuration.value.isReady) delegate.getTools(readonlyContext) else emptyList()
        override suspend fun processLlmRequest(toolContext: ToolContext, llmRequest: LlmRequest): LlmRequest =
            if (settings.configuration.value.isReady) delegate.processLlmRequest(toolContext, llmRequest) else llmRequest
        override fun close() = delegate.close()
    }
    override val id: String = "sandbox"
    override fun revision(): Any = settings.revision.value
    override fun toolsets(spec: AgentBuildSpec): List<Toolset> =
        if (spec.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE && settings.configuration.value.isReady) listOf(sandboxTools, environmentTools) else emptyList()
    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> =
        if (spec.toolAccessMode == ToolAccessMode.ON_DEMAND && settings.configuration.value.isReady) {
            listOf(ToolsetCandidateSource("sandbox:e2b", "E2B sandbox", sandboxTools), ToolsetCandidateSource("environment:e2b", "E2B environment", environmentTools))
        } else emptyList()
    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> =
        if (settings.configuration.value.isReady) listOf(AgentToolCatalogEntry("sandbox:e2b", sandboxTools.getTools(null) + environmentTools.getTools(null))) else emptyList()
}
