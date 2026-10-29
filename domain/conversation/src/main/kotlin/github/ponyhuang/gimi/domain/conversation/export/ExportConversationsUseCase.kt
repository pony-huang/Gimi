package github.ponyhuang.gimi.domain.conversation.export

import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import javax.inject.Inject

/** 统一单个与全部导出的任务保护；选择文件前和实际读取前均需重新校验。 */
class ExportConversationsUseCase @Inject constructor(
    private val repository: ConversationExportRepository,
    private val runtimeGate: AgentRuntimeGate,
) {
    val runtimeState = runtimeGate.state

    suspend fun prepare(sessionId: String? = null): AgentMutationResult<ConversationExportRequest?> =
        runtimeGate.readSessionSnapshot(sessionId) { repository.prepare(sessionId) }

    suspend fun save(
        request: ConversationExportRequest,
        destination: String,
    ): AgentMutationResult<Unit> = runtimeGate.readSessionSnapshot(
        if (request.allSessions) null else request.sessionIds.single(),
    ) {
        repository.export(request, destination)
    }
}
