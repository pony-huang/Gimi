package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Chatbot 分区「当前会话」解析：恢复当前、悬挂回退、空分区新建，激活不做模型适配。 */
class DefaultChatbotConversationResolverTest {
    private val conversations = mockk<ConversationRepository>(relaxed = true)
    private val resolver = DefaultChatbotConversationResolver(conversations)

    @Test fun `resolve restores the remembered conversation without model fitting`() = runTest {
        coEvery { conversations.lastConversationId() } returns "light-1"
        coEvery { conversations.loadMessages("light-1") } returns emptyList()

        assertEquals("light-1", resolver.resolveCurrentOrCreate())

        // Chatbot 会话不做模型适配：激活时默认模型恒为空串。
        coVerify { conversations.activateConversation("light-1", defaultModel = "") }
    }

    @Test fun `resolve discards dangling metadata and falls back to the most recent conversation`() = runTest {
        coEvery { conversations.lastConversationId() } returns "gone"
        coEvery { conversations.loadMessages("gone") } returns null
        coEvery { conversations.listConversations() } returns listOf(conversation("recent"))

        assertEquals("recent", resolver.resolveCurrentOrCreate())

        coVerify { conversations.discardConversationMetadata("gone") }
        coVerify { conversations.activateConversation("recent", any()) }
    }

    @Test fun `resolve creates a conversation when the partition is empty`() = runTest {
        coEvery { conversations.lastConversationId() } returns null
        coEvery { conversations.listConversations() } returns emptyList()
        coEvery { conversations.createConversation(any(), any(), any()) } returns "new-light"

        assertEquals("new-light", resolver.resolveCurrentOrCreate())

        // 语音先建的 Chatbot 会话不预填模型：模型由文本首条提交时锁定。
        coVerify { conversations.createConversation(initialModel = "", activate = true, initialToolConfiguration = null) }
    }

    @Test fun `activate rejects missing sessions`() = runTest {
        coEvery { conversations.loadMessages("light-1") } returns null
        coEvery { conversations.loadMessages("light-2") } returns emptyList()

        assertNull(resolver.activate("light-1"))
        assertNull(resolver.activate(""))
        assertEquals("light-2", resolver.activate("light-2"))
    }

    private fun conversation(id: String) = Conversation(
        id = id,
        title = "t",
        lastMessage = "",
        model = "",
        isLast = false,
        timestamp = 1L,
    )
}
