package github.ponyhuang.gimi.domain.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.SubtaskBatch
import kotlinx.coroutines.flow.Flow

/** 聊天子执行的进度与历史边界，不向领域层暴露 SDK 或数据库类型。 */
interface SubtaskRunRepository {
    fun observe(sessionId: String): Flow<SubtaskBatch>
    suspend fun load(sessionId: String): List<SubtaskBatch>
    suspend fun delete(sessionId: String)
}
