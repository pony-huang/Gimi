package github.ponyhuang.gimi.domain.memory.repository

import github.ponyhuang.gimi.domain.memory.model.MemoryConfiguration
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.model.MemoryRuntimeFailure
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryFeedback
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryPage
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

/** Mem0 云端记忆的用户管理入口；实现负责鉴权和远端调用。 */
interface Mem0MemoryManagementRepository {
    /** 读取当前应用用户的一个记忆分页。 */
    suspend fun loadPage(page: Int, pageSize: Int): ManagedMemoryPage

    /** 永久删除指定云端记忆。 */
    suspend fun delete(memoryId: String)

    /** 为指定记忆提交质量反馈；[reason] 为空时不发送原因。 */
    suspend fun submitFeedback(memoryId: String, feedback: ManagedMemoryFeedback, reason: String?)
}
