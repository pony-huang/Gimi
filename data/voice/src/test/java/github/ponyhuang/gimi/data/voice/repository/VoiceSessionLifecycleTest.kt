package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceDuplexSession
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceEvent
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 保留该所有权回归：焦点丢失与退出并发时，不得提前把麦克风交给新会话。 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionLifecycleTest {
    @Test
    fun busyEventCanCancelItsCollectorAndStillCloseBeforeRetryOrHangUp() = runTest {
        val lifecycle = VoiceSessionLifecycle()
        val backendClosing = CompletableDeferred<Unit>()
        val allowBackendClose = CompletableDeferred<Unit>()
        val backend = mockk<RealtimeVoiceDuplexSession>()
        every { backend.events() } returns flowOf(RealtimeVoiceEvent.Failed(RealtimeVoiceFailure.ConversationBusy))
        val uplink = launch { awaitCancellation() }
        val collector = launch {
            backend.events().collect {
                lifecycle.closeFailedDuplex(this@runTest, backend, currentCoroutineContext()[Job], uplink)
            }
        }
        coEvery { backend.close() } coAnswers {
            assertTrue(collector.isCompleted)
            assertTrue(uplink.isCompleted)
            backendClosing.complete(Unit)
            allowBackendClose.await()
        }

        backendClosing.await()
        val retry = launch { lifecycle.awaitFailedDuplexCleanup() }
        var released = false
        val hangUp = launch { lifecycle.close { released = true } }
        runCurrent()
        assertFalse(retry.isCompleted)
        assertFalse(released)
        allowBackendClose.complete(Unit)
        retry.join()
        hangUp.join()
        assertTrue(released)
        coVerify(exactly = 1) { backend.close() }
    }

    @Test
    fun concurrentClosersBothWaitForResourceReleaseAndReleaseOnlyOnce() = runTest {
        val lifecycle = VoiceSessionLifecycle()
        val release = CompletableDeferred<Unit>()
        var releases = 0
        val first = launch { lifecycle.close { releases++; release.await() } }
        runCurrent()
        assertTrue(lifecycle.closed)
        val second = launch { lifecycle.close { releases++ } }
        runCurrent()
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        release.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, releases)
    }

    @Test
    fun closeWaitsForPendingCommitAndNoNewTurnStartsAfterIt() = runTest {
        val lifecycle = VoiceSessionLifecycle()
        val commit = CompletableDeferred<Unit>()
        val finish = launch { lifecycle.finishPendingTurn { commit.await(); true } }
        runCurrent()
        var released = false
        val close = launch { lifecycle.close { released = true } }
        runCurrent()
        assertFalse(released)
        commit.complete(Unit)
        finish.join()
        close.join()
        var started = false
        assertFalse(lifecycle.finishPendingTurn { started = true; true })
        assertFalse(started)
        assertTrue(released)
    }
}
