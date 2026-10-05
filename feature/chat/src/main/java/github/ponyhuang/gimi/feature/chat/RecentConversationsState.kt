package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.Conversation

/**
 * 最近会话的搜索、多选和删除进度。
 *
 * @property selectedIds 勾选集合，包含被搜索隐藏的选择；展示和删除时过滤受保护会话。
 * @property pendingDeletionIds 打开确认框时固化的 ID 集合，确认后再次检查删除资格。
 * @property isDeleting 批量删除期间锁定搜索和勾选，防止重复提交。
 */
data class RecentConversationsState(
    val query: String = "",
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val pendingDeletionIds: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
)

/** 最近会话面板交互；真实删除只由 ViewModel 执行。 */
sealed interface RecentConversationsAction {
    /** 更新标题/末条消息搜索词。 */
    data class Search(val query: String) : RecentConversationsAction
    /** 进入多选；长按时同时选中指定会话。 */
    data class StartSelection(val sessionId: String? = null) : RecentConversationsAction
    /** 切换一个可删除会话的勾选状态。 */
    data class ToggleSelection(val sessionId: String) : RecentConversationsAction
    /** 全选或取消当前搜索结果中可删除的会话。 */
    data object ToggleSelectAll : RecentConversationsAction
    /** 退出多选并清除勾选。 */
    data object FinishSelection : RecentConversationsAction
    /** 固定待删除集合并请求确认。 */
    data object RequestDeletion : RecentConversationsAction
    /** 取消删除确认。 */
    data object CancelDeletion : RecentConversationsAction
    /** 用户已确认批量删除。 */
    data object ConfirmDeletion : RecentConversationsAction
}

internal fun RecentConversationsState.visibleConversations(conversations: List<Conversation>): List<Conversation> {
    val search = query.trim()
    return if (search.isEmpty()) conversations else conversations.filter {
        it.title.contains(search, ignoreCase = true) || it.lastMessage.contains(search, ignoreCase = true)
    }
}

internal fun ConversationTaskStatus?.preventsDeletion(): Boolean =
    this is ConversationTaskStatus.Running || this is ConversationTaskStatus.WaitingForConfirmation ||
        this is ConversationTaskStatus.WaitingForInput

internal fun ChatUiState.deletableConversationIds(): Set<String> =
    deletableConversationIds(conversations, sessionId, conversationTaskStatuses)

internal fun deletableConversationIds(
    conversations: List<Conversation>,
    currentSessionId: String,
    statuses: Map<String, ConversationTaskStatus>,
): Set<String> = conversations.asSequence()
    .filter { it.id != currentSessionId && !statuses[it.id].preventsDeletion() }
    .map { it.id }.toSet()

internal fun ChatUiState.reduceRecentConversations(action: RecentConversationsAction): ChatUiState {
    val state = recentConversations
    if (state.isDeleting) return this
    val eligible = deletableConversationIds()
    val selected = state.selectedIds.intersect(eligible)
    val next = when (action) {
        is RecentConversationsAction.Search -> state.copy(query = action.query)
        is RecentConversationsAction.StartSelection -> state.copy(
            isSelecting = true,
            selectedIds = selected + setOfNotNull(action.sessionId?.takeIf { it in eligible }),
        )
        is RecentConversationsAction.ToggleSelection -> {
            if (!state.isSelecting || action.sessionId !in eligible) return this
            state.copy(selectedIds = if (action.sessionId in selected) selected - action.sessionId else selected + action.sessionId)
        }
        RecentConversationsAction.ToggleSelectAll -> {
            if (!state.isSelecting) return this
            val visibleIds = state.visibleConversations(conversations).map { it.id }.toSet().intersect(eligible)
            state.copy(selectedIds = if (selected.containsAll(visibleIds)) selected - visibleIds else selected + visibleIds)
        }
        RecentConversationsAction.FinishSelection -> state.copy(isSelecting = false, selectedIds = emptySet(), pendingDeletionIds = emptySet())
        RecentConversationsAction.RequestDeletion -> {
            if (!state.isSelecting) return this
            state.copy(selectedIds = selected, pendingDeletionIds = selected)
        }
        RecentConversationsAction.CancelDeletion -> state.copy(pendingDeletionIds = emptySet())
        RecentConversationsAction.ConfirmDeletion -> return this
    }
    return copy(recentConversations = next)
}
