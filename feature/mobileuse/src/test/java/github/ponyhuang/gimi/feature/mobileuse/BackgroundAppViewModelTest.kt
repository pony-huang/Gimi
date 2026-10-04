package github.ponyhuang.gimi.feature.mobileuse

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplaySession
import github.ponyhuang.gimi.domain.mobileuse.MobileTouch
import github.ponyhuang.gimi.domain.mobileuse.MobileTouchAction
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundAppViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val session = MutableStateFlow<MobileDisplaySession?>(MobileDisplaySession("s1", "chat", 10, 1080, 2400))
    private val small = MutableStateFlow(false)
    private val repository = mockk<MobileUseRepository>(relaxed = true) {
        every { displaySession } returns session
        every { smallWindowEnabled } returns small
    }

    @Test fun manualTouchAndReturnDoNotFinishOrRegisterAnyAiExecution() = runTest {
        val vm = BackgroundAppViewModel(repository)
        coEvery { repository.manualTouch(any(), any()) } returns MobileUseResult("delivered", "")
        coEvery { repository.manualBack("s1") } returns MobileUseResult("delivered", "")
        vm.onAction(BackgroundAppAction.Touch("s1", MobileTouch(MobileTouchAction.DOWN, 1f, 2f, 1, 1)))
        vm.onAction(BackgroundAppAction.Back)
        runCurrent()
        coVerify { repository.manualTouch("s1", any()) }
        coVerify { repository.manualBack("s1") }
        coVerify(exactly = 0) { repository.finishExecution(any()) }
        coVerify(exactly = 0) { repository.registerExecution(any(), any()) }
    }

    @Test fun staleWindowCannotSendInputToReplacementSession() = runTest {
        val vm = BackgroundAppViewModel(repository)
        session.value = session.value!!.copy(id = "s2")
        vm.onAction(BackgroundAppAction.Touch("s1", MobileTouch(MobileTouchAction.DOWN, 1f, 2f, 1, 1)))
        runCurrent()
        coVerify(exactly = 0) { repository.manualTouch(any(), any()) }
        assertEquals("s2", vm.uiState.value.session?.id)
    }

    @Test fun consecutiveMovesKeepLatestPositionButNeverDropDownAndUp() = runTest {
        val received = mutableListOf<MobileTouch>()
        coEvery { repository.manualTouch("s1", any()) } coAnswers {
            received += secondArg<MobileTouch>()
            delay(100)
            MobileUseResult("delivered", "")
        }
        val vm = BackgroundAppViewModel(repository)
        fun send(action: MobileTouchAction, x: Float) = vm.onAction(BackgroundAppAction.Touch("s1", MobileTouch(action, x, 1f, 1, 1)))
        send(MobileTouchAction.DOWN, 0f)
        runCurrent()
        repeat(100) { send(MobileTouchAction.MOVE, it.toFloat()) }
        send(MobileTouchAction.UP, 99f)
        advanceUntilIdle()
        assertEquals(listOf(MobileTouchAction.DOWN, MobileTouchAction.MOVE, MobileTouchAction.UP), received.map { it.action })
        assertEquals(99f, received[1].x)
    }

    @Test fun closingUsesStableSessionIdAndClearsTheWindowState() = runTest {
        coEvery { repository.closeSession("s1") } coAnswers { session.value = null; MobileUseResult("closed", "") }
        val vm = BackgroundAppViewModel(repository)
        vm.onAction(BackgroundAppAction.Menu(true))
        vm.onAction(BackgroundAppAction.Close)
        runCurrent()
        assertNull(vm.uiState.value.session)
        assertFalse(vm.uiState.value.menuExpanded)
        coVerify { repository.closeSession("s1") }
    }

    @Test fun delayedInputFailureFromOldSessionDoesNotAffectReplacementWindow() = runTest {
        coEvery { repository.manualTouch("s1", any()) } coAnswers {
            delay(100)
            MobileUseResult("input_unavailable", "")
        }
        val vm = BackgroundAppViewModel(repository)
        vm.onAction(BackgroundAppAction.Touch("s1", MobileTouch(MobileTouchAction.DOWN, 1f, 2f, 1, 1)))
        runCurrent()
        session.value = session.value!!.copy(id = "s2")
        advanceUntilIdle()
        assertEquals("s2", vm.uiState.value.session?.id)
        assertFalse(vm.uiState.value.inputUnavailable)
    }
    @Test fun rotatingDropsQueuedTouchesAndPreventsDuplicateRequests() = runTest {
        coEvery { repository.manualRotate("s1") } coAnswers {
            delay(100)
            session.value = session.value!!.copy(width = 2400, height = 1080)
            MobileUseResult("delivered", "")
        }
        val vm = BackgroundAppViewModel(repository)
        vm.onAction(BackgroundAppAction.Touch("s1", MobileTouch(MobileTouchAction.DOWN, 1f, 2f, 1, 1)))
        vm.onAction(BackgroundAppAction.Rotate)
        assertTrue(vm.uiState.value.rotating)
        vm.onAction(BackgroundAppAction.Rotate)
        vm.onAction(BackgroundAppAction.Touch("s1", MobileTouch(MobileTouchAction.DOWN, 3f, 4f, 2, 2)))
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.manualRotate("s1") }
        coVerify(exactly = 0) { repository.manualTouch(any(), any()) }
        assertEquals(2400, vm.uiState.value.session?.width)
        assertFalse(vm.uiState.value.rotating)
        assertFalse(vm.uiState.value.rotationUnavailable)
    }

    @Test fun rotationFailureIsDismissibleAndDoesNotAffectReplacementSession() = runTest {
        coEvery { repository.manualRotate("s1") } returns MobileUseResult("rotation_unavailable", "")
        val vm = BackgroundAppViewModel(repository)
        vm.onAction(BackgroundAppAction.Rotate)
        runCurrent()
        assertTrue(vm.uiState.value.rotationUnavailable)
        assertFalse(vm.uiState.value.rotating)
        vm.onAction(BackgroundAppAction.DismissRotationError)
        assertFalse(vm.uiState.value.rotationUnavailable)
        coEvery { repository.manualRotate("s1") } coAnswers {
            delay(100)
            MobileUseResult("rotation_unavailable", "")
        }
        vm.onAction(BackgroundAppAction.Rotate)
        runCurrent()
        session.value = session.value!!.copy(id = "s2")
        advanceUntilIdle()
        assertEquals("s2", vm.uiState.value.session?.id)
        assertFalse(vm.uiState.value.rotationUnavailable)
        assertFalse(vm.uiState.value.rotating)
    }

}
