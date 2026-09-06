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

    private class FakeCheckpoints : ChatSessionCheckpointStore {
        var snapshot = ChatSessionCheckpoint(
            sessionId = "session",
            stateJson = "{\"count\":1}",
            createTime = 1,
            updateTime = 1,
            events = emptyList(),
        )
        var restoreFails = false
        var restoreCalls = 0

        override suspend fun capture(sessionId: String): ChatSessionCheckpoint = snapshot

        override suspend fun restore(checkpoint: ChatSessionCheckpoint) {
            restoreCalls++
            if (restoreFails) throw java.io.IOException("storage unavailable")
            snapshot = checkpoint
        }
    }

    private val dao = FakeDao()
    private val checkpoints = FakeCheckpoints()
    private val user = Messages.fromUser("hello")
    private fun repository() = AdkChatTurnRepository(dao, checkpoints)

    @Test
    fun retryRestoresCheckpointAndDoesNotDuplicateUserMessage() = runTest {
        val first = repository().begin("session", user, emptyList(), null)
        repository().save(first.copy(status = ChatTurnStatus.FAILED))
        // 模拟失败轮往会话里追加了内容（如错误事件），检查点快照因此前进。
        checkpoints.snapshot = checkpoints.snapshot.copy(
            stateJson = "{\"count\":2}",
            events = listOf(CheckpointEvent("error", "inv", 2, "{}")),
        )

        val retry = repository().begin("session", user, emptyList(), first.id)

        assertEquals("{\"count\":1}", checkpoints.snapshot.stateJson)
        assertEquals(emptyList<CheckpointEvent>(), checkpoints.snapshot.events)
        assertEquals(first.id, retry.id)
        assertNotEquals(first.attemptId, retry.attemptId)
        assertEquals(listOf(user), retry.messages)
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
        } catch (expected: StaleChatTurnException) {
            assertEquals(0, checkpoints.restoreCalls)
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

    @Test
    fun restoreFailureLeavesRowIntactForAnotherRetry() = runTest {
        val repo = repository()
        val first = repo.begin("session", user, emptyList(), null)
        repo.save(first.copy(status = ChatTurnStatus.FAILED))
        checkpoints.restoreFails = true
        try {
            repo.begin("session", user, emptyList(), first.id)
            fail("Expected restore failure")
        } catch (expected: java.io.IOException) {
            assertEquals(ChatTurnStatus.FAILED, repository().recover("session")?.status)
        }
        checkpoints.restoreFails = false
        val recovered = repository().begin("session", user, emptyList(), first.id)
        assertEquals(first.id, recovered.id)
    }
}
