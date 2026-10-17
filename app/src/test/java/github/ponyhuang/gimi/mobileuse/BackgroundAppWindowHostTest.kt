package github.ponyhuang.gimi.mobileuse

import android.content.Context
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplaySession
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundAppWindowHostTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val session = MutableStateFlow<MobileDisplaySession?>(null)
    private val repository = mockk<MobileUseRepository> {
        every { displaySession } returns session
        every { smallWindowEnabled } returns MutableStateFlow(true)
    }
    private val host by lazy {
        spyk(BackgroundAppWindowHost(mockk<Context>(), repository)) {
            every { ensureService() } returns true
            every { canOverlay() } returns true
        }
    }

    @Test fun executionCompletionKeepsSmallWindowAndBubbleWhileDisplayExists() = runTest {
        host.start()
        runCurrent()
        session.value = MobileDisplaySession("s1", "chat", 10, 1080, 2400)
        runCurrent()
        assertEquals(BackgroundAppPresentation.BUBBLE, host.presentation.value)
        host.open()
        assertEquals(BackgroundAppPresentation.SMALL_WINDOW, host.presentation.value)
        session.value = session.value!!.copy(completionVersion = 1)
        runCurrent()
        assertEquals(BackgroundAppPresentation.SMALL_WINDOW, host.presentation.value)
        assertEquals("s1", session.value?.id)
        host.open()
        assertEquals(BackgroundAppPresentation.SMALL_WINDOW, host.presentation.value)
        session.value = session.value!!.copy(width = 2400, height = 1080)
        runCurrent()
        assertEquals(BackgroundAppPresentation.SMALL_WINDOW, host.presentation.value)
        host.collapse()
        assertEquals(BackgroundAppPresentation.BUBBLE, host.presentation.value)
        session.value = session.value!!.copy(completionVersion = 2)
        runCurrent()
        assertEquals(BackgroundAppPresentation.BUBBLE, host.presentation.value)
    }

    @Test fun fullscreenStaysOnCompletionAndNewSessionGetsItsOwnEntry() = runTest {
        host.start()
        session.value = MobileDisplaySession("s1", "chat", 10, 1080, 2400)
        runCurrent()
        host.activateFullscreen("s1")
        session.value = session.value!!.copy(completionVersion = 1)
        runCurrent()
        assertEquals(BackgroundAppPresentation.FULLSCREEN, host.presentation.value)
        session.value = MobileDisplaySession("s2", "chat2", 11, 1080, 2400)
        runCurrent()
        assertEquals(BackgroundAppPresentation.BUBBLE, host.presentation.value)
        session.value = null
        runCurrent()
        assertEquals(BackgroundAppPresentation.HIDDEN, host.presentation.value)
    }

    @Test fun hostStartingAfterExecutionFinishesShowsExistingDisplayEntry() = runTest {
        session.value = MobileDisplaySession("s1", "chat", 10, 1080, 2400, completionVersion = 1)
        host.start()
        runCurrent()
        assertEquals(BackgroundAppPresentation.BUBBLE, host.presentation.value)
        host.open()
        assertEquals(BackgroundAppPresentation.SMALL_WINDOW, host.presentation.value)
    }
    @Test fun enablingSmallWindowWithoutPermissionRoutesWithoutSavingPreference() {
        val windowHost = host
        every { windowHost.canOverlay() } returns false
        every { windowHost.openPermissionSettings() } returns Unit
        windowHost.switchWindow(true)
        verify { windowHost.openPermissionSettings() }
        coVerify(exactly = 0) { repository.setSmallWindowEnabled(any()) }
    }

    @Test fun reopeningPreferredSmallWindowWithoutPermissionRoutesToPermissions() {
        val windowHost = host
        session.value = MobileDisplaySession("s1", "chat", 10, 1080, 2400)
        every { windowHost.canOverlay() } returns false
        every { windowHost.openPermissionSettings() } returns Unit
        windowHost.open()
        verify { windowHost.openPermissionSettings() }
        verify(exactly = 0) { windowHost.ensureService() }
    }
}
