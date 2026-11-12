package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentConversationsStateTest {
    @Test
    fun closingSearchClearsQueryAndRestoresFullList() {
        val searching = state().reduceRecentConversations(RecentConversationsAction.OpenSearch)
            .reduceRecentConversations(RecentConversationsAction.Search("camera"))
        assertTrue(searching.recentConversations.isSearching)
        val closed = searching.reduceRecentConversations(RecentConversationsAction.CloseSearch).recentConversations
        assertFalse(closed.isSearching)
        assertEquals("", closed.query)
        assertEquals(conversations, closed.visibleConversations(conversations))
    }

    @Test
    fun selectingSearchResultsAndFinishingSelectionRetainsSearch() {
        val finished = state().reduceRecentConversations(RecentConversationsAction.OpenSearch)
            .reduceRecentConversations(RecentConversationsAction.Search("camera"))
            .reduceRecentConversations(RecentConversationsAction.StartSelection("camera"))
            .reduceRecentConversations(RecentConversationsAction.FinishSelection).recentConversations
        assertTrue(finished.isSearching)
        assertEquals("camera", finished.query)
        assertFalse(finished.isSelecting)
        assertTrue(finished.selectedIds.isEmpty())
    }

    @Test
    fun closingDrawerResetsSearchSelectionAndConfirmation() {
        val closed = state().reduceRecentConversations(RecentConversationsAction.OpenSearch)
            .reduceRecentConversations(RecentConversationsAction.Search("camera"))
            .reduceRecentConversations(RecentConversationsAction.StartSelection("camera"))
            .reduceRecentConversations(RecentConversationsAction.RequestDeletion)
            .reduceRecentConversations(RecentConversationsAction.DrawerClosed).recentConversations
        assertEquals(RecentConversationsState(), closed)
    }

    @Test
    fun closingDrawerDuringDeletionResetsInteractionAndKeepsDeletionInFlight() {
        val deleting = state().copy(recentConversations = RecentConversationsState(
            query = "camera",
            isSearching = true,
            isSelecting = true,
            selectedIds = setOf("camera"),
            pendingDeletionIds = setOf("camera"),
            isDeleting = true,
        ))
        assertEquals(
            RecentConversationsState(isDeleting = true),
            deleting.reduceRecentConversations(RecentConversationsAction.DrawerClosed).recentConversations,
        )
        assertEquals(deleting, deleting.reduceRecentConversations(RecentConversationsAction.CloseSearch))
    }

    private val conversations = listOf(
        Conversation(id = "current", title = "当前会话"),
        Conversation(id = "camera", title = "Camera settings", lastMessage = "相机屏幕偏橙"),
        Conversation(id = "food", title = "广州早茶推荐"),
        Conversation(id = "running", title = "正在执行"),
        Conversation(id = "confirmation", title = "等待确认"),
        Conversation(id = "input", title = "等待输入"),
    )

    private fun state() = ChatUiState(
        sessionId = "current",
        conversations = conversations,
        conversationTaskStatuses = mapOf(
            "running" to ConversationTaskStatus.Running(AgentTaskPhase.GENERATING),
            "confirmation" to ConversationTaskStatus.WaitingForConfirmation(1),
            "input" to ConversationTaskStatus.WaitingForInput,
        ),
    )

    @Test
    fun searchMatchesTitleOrLastMessageIgnoringCaseAndWhitespace() {
        assertEquals(listOf("camera"), RecentConversationsState(query = "  CAMERA  ").visibleConversations(conversations).map { it.id })
        assertEquals(listOf("camera"), RecentConversationsState(query = "相机").visibleConversations(conversations).map { it.id })
        assertEquals(conversations, RecentConversationsState(query = "  ").visibleConversations(conversations))
        assertTrue(RecentConversationsState(query = "missing").visibleConversations(conversations).isEmpty())
    }

    @Test
    fun selectionProtectsCurrentRunningAndWaitingConversations() {
        var state = state().reduceRecentConversations(RecentConversationsAction.StartSelection())
        for (id in listOf("current", "running", "confirmation", "input", "missing", "camera")) {
            state = state.reduceRecentConversations(RecentConversationsAction.ToggleSelection(id))
        }
        assertEquals(setOf("camera"), state.recentConversations.selectedIds)
    }

    @Test
    fun selectAllOnlyAffectsMatchingDeletableConversationsAndPreservesHiddenSelections() {
        var state = state().reduceRecentConversations(RecentConversationsAction.StartSelection("food"))
            .reduceRecentConversations(RecentConversationsAction.Search("camera"))
            .reduceRecentConversations(RecentConversationsAction.ToggleSelectAll)
        assertEquals(setOf("food", "camera"), state.recentConversations.selectedIds)
        state = state.reduceRecentConversations(RecentConversationsAction.ToggleSelectAll)
        assertEquals(setOf("food"), state.recentConversations.selectedIds)
    }

    @Test
    fun confirmationUsesFrozenSelectionAndRechecksProtection() {
        val state = state().reduceRecentConversations(RecentConversationsAction.StartSelection("camera"))
            .reduceRecentConversations(RecentConversationsAction.RequestDeletion)
            .reduceRecentConversations(RecentConversationsAction.ToggleSelection("food"))
        assertEquals(setOf("camera"), state.recentConversations.pendingDeletionIds)
        val changed = state.copy(sessionId = "camera")
            .reduceRecentConversations(RecentConversationsAction.RequestDeletion)
        assertEquals(setOf("food"), changed.recentConversations.pendingDeletionIds)
    }

    @Test
    fun finishingSelectionClearsConfirmationButRetainsSearchAndDeletingLocksInteraction() {
        val state = state().reduceRecentConversations(RecentConversationsAction.Search("camera"))
            .reduceRecentConversations(RecentConversationsAction.StartSelection("camera"))
            .reduceRecentConversations(RecentConversationsAction.RequestDeletion)
        val locked = state.copy(recentConversations = state.recentConversations.copy(isDeleting = true))
        assertEquals(locked, locked.reduceRecentConversations(RecentConversationsAction.FinishSelection))
        val finished = state.reduceRecentConversations(RecentConversationsAction.FinishSelection).recentConversations
        assertFalse(finished.isSelecting)
        assertTrue(finished.selectedIds.isEmpty())
        assertTrue(finished.pendingDeletionIds.isEmpty())
        assertEquals("camera", finished.query)
    }
}
