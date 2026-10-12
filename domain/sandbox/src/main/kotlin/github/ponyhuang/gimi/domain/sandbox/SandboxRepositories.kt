package github.ponyhuang.gimi.domain.sandbox

import kotlinx.coroutines.flow.StateFlow

/** 加密持久化配置；revision 只在成功提交不同设置后变化。 */
interface SandboxSettingsRepository {
    val configuration: StateFlow<SandboxConfiguration>
    val revision: StateFlow<Long>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun saveConnection(connection: SandboxConnection)
}

/** 五个执行能力共享按聊天隔离的懒创建环境。 */
interface SandboxExecutionRepository {
    suspend fun execute(session: SandboxSessionKey, operation: SandboxOperation): SandboxExecutionResult
}
