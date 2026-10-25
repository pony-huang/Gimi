package github.ponyhuang.gimi.domain.a2a.repository

import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 远程 A2A agent 配置的持久化端口；实现方负责加密存储与跨进程一致的 revision 递增。 */
interface RemoteAgentRepository {

    /** 每次配置变更自增的版本号，作为 Agent 运行时缓存键的配置维度。 */
    val revision: StateFlow<Long>

    fun observeAgents(): Flow<List<RemoteAgentConfig>>

    fun currentAgents(): List<RemoteAgentConfig>

    fun agent(id: String): RemoteAgentConfig?

    /** 按 [RemoteAgentConfig.id] 新增或整体替换；无有效 id 时由默认值生成。 */
    fun save(agent: RemoteAgentConfig)

    fun delete(id: String)
}
