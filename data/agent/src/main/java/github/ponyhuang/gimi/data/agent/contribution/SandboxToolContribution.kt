package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.tools.sandbox.SandboxTools
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolsetCandidateSource
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** E2B 工具贡献方，配置 revision 使 Agent 缓存失效，两种加载模式使用同一执行门禁。 */
@Singleton
class SandboxToolContribution @Inject constructor(private val tools: SandboxTools, private val settings: SandboxSettingsRepository) : AgentContribution {
    override val id: String = "sandbox"
    override fun revision(): Any = settings.revision.value
    override fun toolsets(spec: AgentBuildSpec): List<Toolset> =
        if (spec.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE && settings.configuration.value.isReady) listOf(tools) else emptyList()
    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> =
        if (spec.toolAccessMode == ToolAccessMode.ON_DEMAND && settings.configuration.value.isReady) {
            listOf(ToolsetCandidateSource("sandbox:e2b", "E2B sandbox", tools))
        } else emptyList()
    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> =
        tools.getTools(null).takeIf { it.isNotEmpty() }?.let { listOf(AgentToolCatalogEntry("sandbox:e2b", it)) } ?: emptyList()
}
