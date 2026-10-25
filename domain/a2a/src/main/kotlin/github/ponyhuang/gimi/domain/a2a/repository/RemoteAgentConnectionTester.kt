package github.ponyhuang.gimi.domain.a2a.repository

import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentProbeResult

/** 远程 A2A agent 连通性探测端口：按配置拉取 agent card，供编辑器保存前校验。 */
interface RemoteAgentConnectionTester {
    suspend fun test(config: RemoteAgentConfig): RemoteAgentProbeResult
}
