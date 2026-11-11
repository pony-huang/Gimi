package github.ponyhuang.gimi.data.voice.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 保留该所有权回归：焦点丢失与退出并发时，不得提前把麦克风交给新会话。 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionLifecycleTest {
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
