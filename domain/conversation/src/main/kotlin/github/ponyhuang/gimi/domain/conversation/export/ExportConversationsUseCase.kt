package github.ponyhuang.gimi.domain.conversation.export

import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import javax.inject.Inject

/** 目标会话的任务保护：选择文件前和实际读取前均需重新校验，运行中的会话不允许导出。 */
class ExportConversationsUseCase @Inject constructor(
    private val repository: ConversationExportRepository,
    private val runtimeGate: AgentRuntimeGate,
) {
    val runtimeState = runtimeGate.state

    suspend fun prepare(
        target: ConversationExportSession,
    ): AgentMutationResult<ConversationExportRequest> =
        runtimeGate.readSessionSnapshot(target.sessionId) { repository.prepare(target) }

    suspend fun save(
        request: ConversationExportRequest,
        destination: String,
    ): AgentMutationResult<Unit> = runtimeGate.readSessionSnapshot(request.sessionId) {
        repository.export(request, destination)
    }
}
