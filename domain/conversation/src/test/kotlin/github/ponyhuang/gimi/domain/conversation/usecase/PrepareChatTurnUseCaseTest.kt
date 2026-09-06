package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.*
import github.ponyhuang.gimi.domain.conversation.repository.*
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PrepareChatTurnUseCaseTest {
    private val calls = mutableListOf<String>()
    private val original = Messages.fromUser("original")
    private val failed = ChatTurn("turn", "attempt", "session", original, listOf(original), ChatTurnStatus.FAILED)
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
    private val runner = object : ChatAgentRepository {
        override suspend fun send(sessionId: String, selection: ModelSelection, text: String, fileAttachments: List<FileAttachment>, toolConfiguration: ConversationToolConfiguration?): Flow<ChatRunEvent> = emptyFlow()
        override suspend fun respondToToolConfirmation(sessionId: String, confirmationCallId: String, confirmed: Boolean): Flow<ChatRunEvent> = emptyFlow()
        override suspend fun respondToInputRequest(sessionId: String, callId: String, toolName: String, value: String): Flow<ChatRunEvent> = emptyFlow()
        override suspend fun releaseSession(sessionId: String) { calls += "release" }
    }
    private val turns = object : ChatTurnRepository {
        override suspend fun recover(sessionId: String): ChatTurn? = failed
        override suspend fun begin(sessionId: String, userMessage: Message, history: List<Message>, retryTurnId: String?): ChatTurn {
            calls += "begin:$retryTurnId"
            return failed.copy(userMessage = userMessage, messages = history + userMessage)
        }
        override suspend fun save(turn: ChatTurn) = Unit
        override suspend fun finish(sessionId: String, attemptId: String) = Unit
        override suspend fun delete(sessionId: String) = Unit
    }
    private val prepare = PrepareChatTurnUseCase(turns, attachments, runner)

    @Test fun retryValidatesBeforeRewindingAndKeepsOneOriginalUserMessage() = runBlocking {
        val result = prepare("session", "ignored", emptyList(), listOf(original), failed, true)
        assertEquals(listOf("validate", "release", "begin:turn"), calls)
        assertEquals(listOf(original), result.messages)
    }

    @Test fun editingPreservesMessageIdentityAndReplacesItsText() = runBlocking {
        val result = prepare("session", "edited", emptyList(), emptyList(), failed)
        assertEquals(original.id, result.userMessage.id)
        assertEquals("edited", result.userMessage.textParts.single().text)
        assertEquals(listOf("read", "release", "begin:turn"), calls)
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
