package github.ponyhuang.gimi.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollPolicyTest {
    @Test
    fun refreshedOrAppendedRepliesKeepHistoryReadingPosition() {
        assertFalse(shouldScrollChatToLatest(false, false, false, false))
    }

    @Test
    fun firstEntryPositionsAtLatestMessage() {
        assertTrue(shouldScrollChatToLatest(true, false, false, false))
    }

    @Test
    fun explicitConversationNavigationPositionsAtLatestMessage() {
        assertTrue(shouldScrollChatToLatest(false, true, false, false))
    }

    @Test
    fun sendingNewMessageReturnsToLatestWhileReadingHistory() {
        assertTrue(shouldScrollChatToLatest(false, false, true, false))
    }

    @Test
    fun readersAtBottomContinueFollowingReplies() {
        assertTrue(shouldScrollChatToLatest(false, false, false, true))
    }
}
