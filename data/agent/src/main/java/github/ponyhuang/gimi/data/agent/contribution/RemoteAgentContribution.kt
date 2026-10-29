package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.agents.BaseAgent
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.a2a.RemoteAgentRuntimeCache
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程 A2A agent 贡献方：把用户配置的远程 agent 以 sub-agent 形式挂入 agent 树，
 * 会话中由模型经 `transfer_to_agent` 委派。不贡献工具；配置 revision 由
 * [RemoteAgentRepository] 提供，agent 实例的构建/缓存/降级收敛在 [RemoteAgentRuntimeCache]。
 */
@Singleton
class RemoteAgentContribution @Inject constructor(
    private val repository: RemoteAgentRepository,
    private val runtimeCache: RemoteAgentRuntimeCache,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any? = repository.revision.value to runtimeCache.retryRevision

    override suspend fun subAgents(spec: AgentBuildSpec): List<BaseAgent> =
        runtimeCache.currentSubAgents(spec)

    private companion object {
        const val ID: String = "remote_agent"
    }
}
