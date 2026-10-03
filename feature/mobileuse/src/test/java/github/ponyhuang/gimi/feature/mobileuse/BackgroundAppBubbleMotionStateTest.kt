package github.ponyhuang.gimi.feature.mobileuse

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundAppBubbleMotionStateTest {
    private val bounds = BackgroundAppBubbleBounds(0f, 304f, 24f, 640f, 28f)

    @Test
    fun releaseInMiddleKeepsPositionAfterIdle() = runTest {
        val state = motion(Offset(160f, 200f))
        state.beginDrag()
        state.dragBy(Offset(-40f, 80f))
        state.endDrag()

        advanceTimeBy(5000)
        assertEquals(Offset(120f, 280f), state.position)
    }

    @Test
    fun approachingEitherEdgeDoesNotHideOrSnap() = runTest {
        for (x in listOf(.25f, 12f, 292f, 303.75f)) {
            val state = motion(Offset(x, 200f))
            state.endDrag()
            advanceTimeBy(5000)
            assertEquals(Offset(x, 200f), state.position)
        }
    }

    @Test
    fun touchingEitherEdgeWaitsThenMovesContinuouslyToHalfHidden() = runTest {
        for ((edge, hidden) in listOf(0f to -28f, 304f to 332f)) {
            val state = motion(Offset(edge, 200f))
            runCurrent()
            advanceTimeBy(1999)
            assertEquals(edge, state.position.x, 0f)

            advanceTimeBy(161)
            assertTrue(state.position.x > minOf(edge, hidden))
            assertTrue(state.position.x < maxOf(edge, hidden))
            assertEquals(200f, state.position.y, 0f)

            advanceTimeBy(500)
            assertEquals(hidden, state.position.x, .001f)
        }
    }

    @Test
    fun releasingBeyondEitherEdgeStillHidesOnlyHalf() = runTest {
        for ((x, hidden) in listOf(-10f to -28f, 314f to 332f)) {
            val state = motion(Offset(x, 200f))
            runCurrent()
            advanceTimeBy(2500)
            assertEquals(hidden, state.position.x, .001f)
        }
    }

    @Test
    fun draggingFromHalfHiddenDoesNotJumpBackToEdge() = runTest {
        val state = motion(Offset(304f, 200f))
        runCurrent()
        advanceTimeBy(2500)
        assertEquals(332f, state.position.x, .001f)

        state.beginDrag()
        assertEquals(332f, state.position.x, .001f)
        state.dragBy(Offset(-80f, 10f))
        state.endDrag()
        advanceTimeBy(5000)
        assertEquals(Offset(252f, 210f), state.position)
    }

    @Test
    fun draggingInterruptsAnActiveHideAtItsVisiblePosition() = runTest {
        val state = motion(Offset(0f, 200f))
        runCurrent()
        advanceTimeBy(2160)
        val current = state.position
        assertTrue(current.x > -28f && current.x < 0f)

        state.beginDrag()
        advanceTimeBy(1000)
        assertEquals(current, state.position)
        state.dragBy(Offset(90f, 0f))
        state.endDrag()
        advanceTimeBy(5000)
        assertEquals(current + Offset(90f, 0f), state.position)
    }

    @Test
    fun draggingCancelsPendingHideUntilReleased() = runTest {
        val state = motion(Offset(0f, 200f))
        runCurrent()
        advanceTimeBy(1500)
        state.beginDrag()
        advanceTimeBy(5000)
        assertEquals(Offset(0f, 200f), state.position)

        state.endDrag()
        runCurrent()
        advanceTimeBy(2500)
        assertEquals(-28f, state.position.x, .001f)
    }

    @Test
    fun boundsKeepHalfOfBubbleVisibleAndRespectSystemInsets() = runTest {
        val state = motion(Offset(160f, 200f))
        state.beginDrag()
        state.dragBy(Offset(-1000f, -1000f))
        assertEquals(Offset(-28f, 24f), state.position)
        state.dragBy(Offset(2000f, 2000f))
        assertEquals(Offset(332f, 640f), state.position)
    }

    @Test
    fun boundsChangeCancelsTheOldEdgeAnimationAndClampsKeyboardArea() = runTest {
        val state = motion(Offset(304f, 600f))
        runCurrent()
        advanceTimeBy(2160)
        val currentX = state.position.x

        state.updateBounds(bounds.copy(right = 600f, bottom = 300f))
        advanceTimeBy(5000)
        assertEquals(Offset(currentX, 300f), state.position)
    }

    @Test
    fun unchangedBoundsDoNotRestartTheIdleTimer() = runTest {
        val state = motion(Offset(304f, 200f))
        runCurrent()
        repeat(10) {
            advanceTimeBy(250)
            state.updateBounds(bounds)
        }
        assertEquals(332f, state.position.x, .001f)
    }

    @Test
    fun slowDraggingAccumulatesSubpixelMovement() = runTest {
        val state = motion(Offset(100f, 200f))
        state.beginDrag()
        repeat(10) { state.dragBy(Offset(.1f, .1f)) }
        assertEquals(101f, state.position.x, .001f)
        assertEquals(201f, state.position.y, .001f)
    }

    @Test
    fun stopCancelsBothPendingAndRunningHide() = runTest {
        for (elapsed in listOf(1000L, 2160L)) {
            val state = motion(Offset(304f, 200f))
            runCurrent()
            advanceTimeBy(elapsed)
            state.stop()
            val current = state.position
            advanceTimeBy(5000)
            assertEquals(current, state.position)
        }
    }

    private fun TestScope.motion(position: Offset): BackgroundAppBubbleMotionState {
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000)
            }
        }
        return BackgroundAppBubbleMotionState(CoroutineScope(backgroundScope.coroutineContext + clock), position)
            .also { it.updateBounds(bounds) }
    }
}
