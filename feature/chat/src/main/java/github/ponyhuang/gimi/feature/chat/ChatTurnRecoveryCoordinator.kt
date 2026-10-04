package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import kotlinx.coroutines.flow.StateFlow

/** 保留失败/停止轮的展示快照；原执行重试仍交给统一发送入口管理 lease 与 token。 */
internal class ChatTurnRecoveryCoordinator(
    private val uiState: StateFlow<ChatUiState>,
    private val runtimeFor: (String) -> ChatSessionRuntime,
    private val publishRuntime: (ChatSessionRuntime) -> Unit,
    private val resend: (ChatTurn) -> Unit,
) {
    fun recordFailure(sessionId: String, turn: ChatTurn) {
        val runtime = runtimeFor(sessionId)
        runtime.lastTurn = turn.copy(status = ChatTurnStatus.FAILED, messages = runtime.messages)
        publishRuntime(runtime)
    }

    /** SDK 恢复原 invocation，不编辑历史、不追加用户消息，也不额外要求重复执行确认。 */
    fun retry() {
        val state = uiState.value
        if (state.isAgentRunning) return
        val failedTurn = state.failedTurn ?: return
        resend(failedTurn)
    }
}
