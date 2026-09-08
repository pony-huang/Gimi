package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.data.conversation.local.ChatTurnDao
import github.ponyhuang.gimi.data.conversation.local.ChatTurnEntity
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.conversation.repository.StaleChatTurnException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ChatTurnRecoveryTest {

    private class FakeDao : ChatTurnDao {
        var row: ChatTurnEntity? = null
        override suspend fun get(sessionId: String): ChatTurnEntity? =
            row?.takeIf { it.sessionId == sessionId }

        override suspend fun put(entity: ChatTurnEntity) {
            row = entity
        }

        override suspend fun finish(sessionId: String, attemptId: String) {
            if (row?.sessionId == sessionId && row?.attemptId == attemptId) row = null
        }

        override suspend fun delete(sessionId: String) {
            if (row?.sessionId == sessionId) row = null
        }
    }

    private val dao = FakeDao()
    private val user = Messages.fromUser("hello")
    private fun repository() = AdkChatTurnRepository(dao)

    @Test
    fun retryPreservesRewindBoundaryAndDoesNotDuplicateUserMessage() = runTest {
        val first = repository().begin("session", user, emptyList(), null)
        repository().save(
            first.copy(
                status = ChatTurnStatus.FAILED,
                rewindBeforeInvocationId = "failed-invocation",
            ),
        )

        val retry = repository().begin("session", user, emptyList(), first.id)

        assertEquals(first.id, retry.id)
        assertNotEquals(first.attemptId, retry.attemptId)
        assertEquals(listOf(user), retry.messages)
        assertEquals("failed-invocation", retry.rewindBeforeInvocationId)
    }

    @Test
    fun interruptedTurnCarriesItsInvocationAsTheAdkRewindBoundary() = runTest {
        val first = repository().begin("session", user, emptyList(), null)
        val interrupted = repository().recover("session")

        val retry = repository().begin("session", user, emptyList(), first.id)

        assertEquals(first.attemptId, interrupted?.rewindBeforeInvocationId)
        assertEquals(first.attemptId, retry.rewindBeforeInvocationId)
    }

    @Test
    fun runningTurnRestartIsMarkedInterruptedAndStillRetryable() = runTest {
        val first = repository().begin("session", user, emptyList(), null)
        val recovered = repository().recover("session")
        assertEquals(ChatTurnStatus.INTERRUPTED, recovered?.status)
        assertEquals(first.id, recovered?.id)
    }

    @Test
    fun failedTurnRetainsTextAttachmentsAndToolWarningAcrossRestart() = runTest {
        val first = repository().begin("session", user, emptyList(), null)
        val assistant = Messages.fromAssistant().copy(
            textParts = listOf(TextPart(text = "partial answer")),
        )
        val failed = first.copy(
            status = ChatTurnStatus.FAILED,
            hasToolCalls = true,
            messages = listOf(user, assistant, Messages.fromError("offline")),
        )
        repository().save(failed)

        assertEquals(failed, repository().recover("session"))
    }

    @Test
    fun failureReplacedByNewMessageIsNoLongerRewindable() = runTest {
        val repo = repository()
        val first = repo.begin("session", user, emptyList(), null)
        repo.save(first.copy(status = ChatTurnStatus.FAILED))
        // 用户失败后重新发送 → 全新发送替换该行。
        repo.begin("session", Messages.fromUser("new message"), emptyList(), null)

        try {
            repo.begin("session", user, emptyList(), first.id)
            fail("Expected stale turn exception")
        } catch (_: StaleChatTurnException) {
            // Expected: a newer turn owns the session row.
        }
    }

    @Test
    fun lateAttemptCannotOverwriteOrRemoveNewAttempt() = runTest {
        val repo = repository()
        val first = repo.begin("session", user, emptyList(), null)
        repo.save(first.copy(status = ChatTurnStatus.FAILED))
        val second = repo.begin("session", user, emptyList(), first.id)
        // 旧尝试的迟到写回与 finish 都不能影响当前尝试。
        repo.save(first.copy(status = ChatTurnStatus.FAILED))
        repo.finish("session", first.attemptId)

        assertEquals(second.attemptId, repository().recover("session")?.attemptId)
    }

}
