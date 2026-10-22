package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import org.junit.Assert.*
import org.junit.Test

/** 当前错误迁移到状态卡时，保护历史内容和原始消息快照。 */
class ChatRequestStatusTest {
    @Test
    fun latestErrorMovesToStatusCardWhileHistoricalErrorRemains() {
        val oldUser = Messages.fromUser("旧请求")
        val oldError = Messages.fromError("旧错误")
        val user = Messages.fromUser("新请求")
        val error = Messages.fromError("连接中断")
        val state = state(listOf(oldUser, oldError, user, error))

        assertEquals("连接中断", state.requestStatus()?.error)
        val displayed = state.listItems.withoutCurrentRequestErrors()
        val answers = displayed.filterIsInstance<ChatListItem.AssistantTurn>().flatMap { it.timeline.segments }
            .filterIsInstance<TurnSegment.Answer>().map { it.message }
        assertEquals(listOf(oldError), answers)
        assertEquals(error, state.messages.last())
    }

    @Test
    fun preparationFailureDoesNotReuseThePreviousRequestsError() {
        val history = listOf(Messages.fromUser("旧请求"), Messages.fromError("旧错误"), Messages.fromUser("新请求"))
        val state = state(history)
        assertNull(state.requestStatus()?.error)
        assertEquals(state.listItems, state.listItems.withoutCurrentRequestErrors())
    }

    @Test
    fun errorWithPartialAnswerPreservesAnswerAndOriginalErrorRecord() {
        val error = Messages.fromError("断开连接").copy(textParts = listOf(TextPart(text = "已经生成的内容")))
        val state = state(listOf(Messages.fromUser("请求"), error))
        val displayed = state.listItems.withoutCurrentRequestErrors().filterIsInstance<ChatListItem.AssistantTurn>()
            .last().timeline.segments.filterIsInstance<TurnSegment.Answer>().single().message
        assertEquals(error.textParts, displayed.textParts)
        assertNull(displayed.error)
        assertEquals("断开连接", state.messages.last().error)
    }

    @Test
    fun retryUsesProgressStatusInsteadOfStaleFailure() {
        val failed = state(listOf(Messages.fromUser("请求"), Messages.fromError("断开连接")))
        val retrying = failed.copy(isAgentRunning = true, isRetryingFailedTurn = true)
        assertEquals(ChatRequestStatus(isRetrying = true), retrying.requestStatus())
        assertNull(failed.copy(isAgentRunning = true).requestStatus())
        assertNull(failed.copy(failedTurn = null).requestStatus())
    }

    @Test
    fun hiddenInteractionTurnIsTreatedAsTheCurrentTurn() {
        val user = Messages.fromUser("带卡片的请求")
        val historical = Messages.fromError("历史错误")
        val current = Messages.fromError("交互执行失败")
        val state = state(listOf(user, historical, current)).copy(listItems = listOf(
            ChatListItem.UserMessage(user),
            ChatListItem.AssistantTurn(TurnTimeline(user.id, false, listOf(TurnSegment.Answer(historical)))),
            ChatListItem.AssistantTurn(TurnTimeline("hidden-interaction", false, listOf(TurnSegment.Answer(current)))),
        ))
        assertEquals("交互执行失败", state.requestStatus()?.error)
        val displayed = state.listItems.withoutCurrentRequestErrors()
        assertEquals(listOf(TurnSegment.Answer(historical)), (displayed[1] as ChatListItem.AssistantTurn).timeline.segments)
        assertTrue((displayed.last() as ChatListItem.AssistantTurn).timeline.segments.isEmpty())
    }

    private fun state(messages: List<github.ponyhuang.gimi.domain.conversation.model.Message>): ChatUiState {
        val user = messages.last { it.role == github.ponyhuang.gimi.domain.conversation.model.MessageRole.User }
        return ChatUiState(messages = messages, listItems = messages.toChatListItems(TimelineActivityState()),
            failedTurn = ChatTurn("turn", "session", user, messages, ChatTurnStatus.FAILED))
    }
}
