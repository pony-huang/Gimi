package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.*
import github.ponyhuang.gimi.domain.conversation.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PrepareChatTurnUseCaseTest {
    private val calls = mutableListOf<String>()
    private val original = Messages.fromUser("original")
    private val failed = ChatTurn("turn", "session", original, listOf(original), ChatTurnStatus.FAILED)
    private var attachmentFailure: Exception? = null
    private val attachments = object : ChatAttachmentRepository {
        override suspend fun read(sessionId: String, attachments: List<DraftAttachment>): List<FileAttachment> {
            calls += "read"
            attachmentFailure?.let { throw it }
            return emptyList()
        }
        override suspend fun deleteDrafts(attachments: List<DraftAttachment>) = Unit
        override suspend fun deleteSession(sessionId: String) = Unit
    }
    private val prepare = PrepareChatTurnUseCase(attachments)

    @Test fun retryPreservesOriginalMessageAndPartialOutputWithoutReadingAttachments() = runBlocking {
        val partial = Messages.fromAssistant().copy(textParts = listOf(TextPart(text = "partial")))
        val turn = failed.copy(messages = listOf(original, partial))
        val result = prepare("session", "ignored", emptyList(), turn.messages, turn)
        assertEquals(turn.messages, result.messages)
        assertEquals(original, result.userMessage)
        assertEquals(turn.id, result.id)
        assertEquals(ChatTurnStatus.RUNNING, result.status)
        assertTrue(calls.isEmpty())
    }

    @Test fun newMessageAfterFailureAppendsInsteadOfReplacingOriginal() = runBlocking {
        val result = prepare("session", "补充说明", emptyList(), failed.messages)
        assertEquals(listOf("original", "补充说明"), result.messages.map { it.textParts.single().text })
        assertNotEquals(original.id, result.userMessage.id)
        assertEquals(listOf("read"), calls)
    }

    @Test fun cancellationPropagatesWithoutStartingAnAttempt() = runBlocking {
        attachmentFailure = CancellationException("cancelled")
        try {
            prepare("session", "hello", emptyList(), emptyList())
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals(listOf("read"), calls)
        }
    }
}
