package github.ponyhuang.gimi.domain.memory.repository

import github.ponyhuang.gimi.domain.memory.model.MemoryConfiguration
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.model.MemoryRuntimeFailure
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 记忆后端配置仓库；实现负责对 API Key 做安全持久化。 */
interface MemorySettingsRepository {
    val configuration: StateFlow<MemoryConfiguration>

    /** 保存配置；[apiKey] 为 null 时保留已有密钥。 */
    suspend fun save(memoryEnabled: Boolean, mem0Enabled: Boolean, apiKey: String?)
}

/** 记忆运行状态通道，负责连续同类失败去重及恢复复位。 */
interface MemoryRuntimeStatus {
    val failures: SharedFlow<MemoryRuntimeFailure>

    fun reportFailure(operation: MemoryOperation)

    fun reportSuccess(operation: MemoryOperation)
}
