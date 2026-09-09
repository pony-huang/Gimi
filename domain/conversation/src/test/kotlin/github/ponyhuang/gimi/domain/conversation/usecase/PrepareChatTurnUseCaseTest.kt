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
        override suspend fun validateSaved(attachments: List<FileAttachment>) {
            calls += "validate"
            attachmentFailure?.let { throw it }
        }
        override suspend fun createDrafts(attachments: List<FileAttachment>) = emptyList<DraftAttachment>()
        override suspend fun deleteDrafts(attachments: List<DraftAttachment>) = Unit
        override suspend fun deleteSession(sessionId: String) = Unit
    }
    private val prepare = PrepareChatTurnUseCase(attachments)

    @Test fun retryValidatesBeforeRewindingAndKeepsOneOriginalUserMessage() = runBlocking {
        val result = prepare("session", "ignored", emptyList(), listOf(original), failed, true)
        assertEquals(listOf("validate"), calls)
        assertEquals(listOf(original), result.messages)
        assertEquals(failed.id, result.id)
        assertEquals(failed.rewindBeforeInvocationId, result.rewindBeforeInvocationId)
    }

    @Test fun editingPreservesMessageIdentityAndReplacesItsText() = runBlocking {
        val result = prepare("session", "edited", emptyList(), emptyList(), failed)
        assertEquals(original.id, result.userMessage.id)
        assertEquals("edited", result.userMessage.textParts.single().text)
        assertEquals(listOf("read"), calls)
    }

    @Test fun unreadableAttachmentNeverChangesHistory() = runBlocking {
        attachmentFailure = java.io.IOException("missing attachment")
        try {
            prepare("session", "", emptyList(), emptyList(), failed, true)
            fail("Expected attachment failure")
        } catch (expected: java.io.IOException) {
            assertEquals(listOf("validate"), calls)
        }
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
