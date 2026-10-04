package github.ponyhuang.gimi.data.mobileuse

import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Bundle
import android.os.SystemClock
import android.os.RemoteException
import android.os.DeadObjectException
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileTouch
import github.ponyhuang.gimi.domain.mobileuse.MobileTouchAction
import io.mockk.every
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rikka.shizuku.Shizuku

class ShizukuMobileUseRepositoryTest {
    private val connections = mutableListOf<ServiceConnection>()
    private val enabled = MutableStateFlow(false)
    private val preferences = mockk<MobileUsePreferences> {
        every { enabled } returns this@ShizukuMobileUseRepositoryTest.enabled
        every { smallWindow } returns MutableStateFlow(false)
        every { setEnabled(any()) } answers { this@ShizukuMobileUseRepositoryTest.enabled.value = firstArg() }
    }
    private val context = mockk<Context>()
    private val repository = ShizukuMobileUseRepository(context, mockk(), preferences)

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun disabledFeatureRejectsEveryDisplayActionWithoutAccessingShizuku() = runBlocking {
        assertEquals(MobileUseAvailability.DISABLED, repository.availability())
        assertEquals("disabled", repository.observe("task").status)
        assertEquals("disabled", repository.launch("task", "com.example.app").status)
        assertEquals("disabled", repository.click("task", "snapshot", "element").status)
        assertEquals("disabled", repository.setText("task", "snapshot", "element", "hello").status)
        assertEquals("disabled", repository.tap("task", "snapshot", 1, 1).status)
        assertEquals("disabled", repository.tapRelative("task", "snapshot", 500, 500).status)
        assertEquals("disabled", repository.swipe("task", "snapshot", 1, 1, 2, 2, 100).status)
        assertEquals("disabled", repository.back("task", "snapshot").status)
        assertEquals("disabled", repository.typeText("task", "snapshot", 1, 1, "hello").status)
        assertEquals("not_sent", repository.click("task", "snapshot", "element").actionStatus)
        repository.requestPermission(1)
        confirmVerified(context)
    }

    @Test
    fun disablingStopsActiveDisplayAndReenablingAllowsANewOwner() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("first-task", "chat1")
        assertTrue(repository.enabled.value)
        assertEquals("operation_failed", repository.observe("first-task").status)
        assertEquals(MobileUseAvailability.BUSY, repository.availability())
        repository.setEnabled(false)
        assertFalse(repository.enabled.value)
        assertEquals("disabled", repository.observe("first-task").status)
        verify(exactly = 1) { remote.stop() }
        verify(exactly = 1) { Shizuku.unbindUserService(any(), any(), true) }

        repository.setEnabled(true)
        repository.registerExecution("second-task", "chat2")
        assertEquals(MobileUseAvailability.READY, repository.availability())
        assertEquals("operation_failed", repository.observe("second-task").status)
        verify(exactly = 2) { remote.createDisplay() }
        repository.setEnabled(false)
    }

    @Test
    fun displaySurvivesTurnCompletionAndManualInputDoesNotRequireAnExecution() = runBlocking {
        val remote = connectRemote()
        every { remote.touch(any(), any(), any(), any(), any(), any()) } returns true
        repository.setEnabled(true)
        repository.registerExecution("first", "chat")
        repository.observe("first")
        val id = repository.displaySession.value!!.id
        assertEquals("execution_finished", repository.finishExecution("first").status)
        assertEquals(1L, repository.displaySession.value!!.completionVersion)
        assertEquals("not_active", repository.finishExecution("first").status)
        assertEquals(1L, repository.displaySession.value!!.completionVersion)
        assertEquals(id, repository.displaySession.value!!.id)
        val touch = MobileTouch(MobileTouchAction.DOWN, 10f, 20f, 100, 100)
        assertEquals("delivered", repository.manualTouch(id, touch).status)
        assertEquals("delivered", repository.manualBack(id).status)
        repository.registerExecution("other", "other-chat")
        assertEquals("busy", repository.observe("other").status)
        repository.finishExecution("other")
        repository.registerExecution("next", "chat")
        repository.observe("next")
        assertEquals(id, repository.displaySession.value!!.id)
        verify(exactly = 1) { remote.createDisplay() }
        verify(exactly = 0) { remote.stop() }
        repository.closeSession(id)
        assertEquals("session_closed", repository.observe("next").status)
        assertEquals("session_lost", repository.manualTouch(id, touch).status)
        repository.finishExecution("next")
        repository.registerExecution("new", "chat")
        repository.observe("new")
        assertFalse(id == repository.displaySession.value!!.id)
        assertEquals("not_active", repository.closeSession(id).status)
        verify(exactly = 2) { remote.createDisplay() }
        verify(exactly = 1) { remote.stop() }
        repository.setEnabled(false)
    }

    @Test
    fun manualRotationUpdatesGeometryWithoutRecreatingTheSessionOrClaimingExecution() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("first", "chat")
        repository.observe("first")
        repository.finishExecution("first")
        val before = repository.displaySession.value!!
        every { remote.rotateDisplay(10) } returns intArrayOf(2400, 1080, 320)
        every { remote.geometry(10) } returns intArrayOf(2400, 1080, 320)
        val result = repository.manualRotate(before.id)
        assertEquals("delivered", result.status)
        assertEquals("delivered", result.actionStatus)
        assertEquals(2400, result.width)
        assertEquals(1080, result.height)
        assertEquals(before.copy(width = 2400, height = 1080), repository.displaySession.value)
        every { remote.rotateDisplay(10) } returns intArrayOf(1080, 2400, 320)
        every { remote.geometry(10) } returns intArrayOf(1080, 2400, 320)
        assertEquals("delivered", repository.manualRotate(before.id).status)
        assertEquals(before, repository.displaySession.value)
        verify(exactly = 1) { remote.createDisplay() }
        verify(exactly = 0) { remote.stop() }
        repository.setEnabled(false)
    }

    @Test
    fun rotationRejectsOldWindowsAndRetainsSessionWhenUnsupported() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("first", "chat")
        repository.observe("first")
        val before = repository.displaySession.value!!
        assertEquals("session_lost", repository.manualRotate("old-window").status)
        verify(exactly = 0) { remote.rotateDisplay(any()) }
        every { remote.rotateDisplay(10) } throws IllegalStateException("Unsupported")
        assertEquals("rotation_unavailable", repository.manualRotate(before.id).status)
        assertEquals(before, repository.displaySession.value)
        every { remote.rotateDisplay(10) } throws RemoteException()
        assertEquals("rotation_unavailable", repository.manualRotate(before.id).status)
        every { remote.rotateDisplay(10) } returns intArrayOf(0, 1080, 320)
        assertEquals("rotation_unavailable", repository.manualRotate(before.id).status)
        assertEquals(before, repository.displaySession.value)
        repository.setEnabled(false)
    }

    private fun connectRemote(): IMobileUseService {
        mockkStatic(Shizuku::class)
        val packageManager = mockk<PackageManager> {
            every { getPackageInfo("moe.shizuku.privileged.api", 0) } returns mockk<PackageInfo>()
        }
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "github.ponyhuang.gimi"
        every { Shizuku.pingBinder() } returns true
        every { Shizuku.getUid() } returns 2000
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_GRANTED
        val remote = mockk<IMobileUseService>(relaxed = true) {
            every { uid() } returns 2000
            every { createDisplay() } returns 10
            // 截图不可用时仍保留后台任务；关闭开关必须清理该任务，而非等待下一次观察。
            every { capture() } throws IllegalStateException("Frame temporarily unavailable")
            every { geometry(any()) } returns intArrayOf(1080, 2400, 320)
        }
        val binder = mockk<IBinder> {
            every { queryLocalInterface(any()) } returns remote
        }
        every { Shizuku.bindUserService(any(), any()) } answers {
            val candidate = secondArg<ServiceConnection>()
            connections += candidate
            candidate.onServiceConnected(mockk(), binder)
        }
        every { Shizuku.unbindUserService(any(), any(), true) } returns Unit

        return remote
    }

    @Test
    fun animatedFeedAdvertisesSwipeAndSendsItOnceWithoutSettledPixels() = runBlocking {
        val remote = connectAnimatedRemote()
        repository.setEnabled(true)
        repository.registerExecution("feed", "chat")
        val observation = repository.observe("feed").observation!!
        assertEquals("changing", observation.reason)
        assertTrue("swipe_coordinates" in observation.actionModes)
        val result = repository.swipe("feed", observation.id, 450, 1200, 450, 400, 450)
        assertEquals("swiped", result.status)
        assertEquals("delivered", result.actionStatus)
        assertEquals("changing", result.observation!!.reason)
        val repeated = repository.swipe("feed", observation.id, 450, 1200, 450, 400, 450)
        assertEquals("stale_observation", repeated.status)
        assertEquals("not_sent", repeated.actionStatus)
        verify(exactly = 1) { remote.swipe(10, 450, 1200, 450, 400, 450) }
        repository.setEnabled(false)
    }

    @Test
    fun animatedFeedAllowsTapWhenOnlyUnrelatedPixelsChange() = runBlocking {
        val remote = connectAnimatedRemote()
        repository.setEnabled(true)
        repository.registerExecution("feed", "chat")
        val observation = repository.observe("feed").observation!!
        assertEquals("changing", observation.reason)
        assertTrue("pixel_coordinates" in observation.actionModes)
        val result = repository.tap("feed", observation.id, 450, 1050)
        assertEquals("tapped", result.status)
        assertEquals("delivered", result.actionStatus)
        verify(exactly = 1) { remote.tap(10, 450, 1050) }
        repository.setEnabled(false)
    }

    private fun connectAnimatedRemote(): IMobileUseService {
        val remote = connectRemote()
        mockkStatic(SystemClock::class)
        var clockMs = 10_000L
        var sequence = 0L
        // 注入单调时钟与持续变化的横幅，不依赖真机或六秒实际超时。
        every { SystemClock.elapsedRealtime() } answers { clockMs += 1_000; clockMs }
        every { remote.capture() } answers {
            val capturedAt = clockMs
            val currentSequence = ++sequence
            val pixels = IntArray(64 * 96) { 100 }.apply {
                for (index in 0..15) this[index] = if (currentSequence % 2 == 0L) 200 else 100
            }
            mockk<Bundle> {
                every { getByteArray("jpeg") } returns byteArrayOf(currentSequence.toByte())
                every { getIntArray("samples") } returns pixels
                every { getInt("width") } returns 1080
                every { getInt("height") } returns 2400
                every { getLong("capturedAtMs") } returns capturedAt
                every { getLong("sequence") } returns currentSequence
                every { getLong("generation") } returns 1
                every { getString("error") } returns null
            }
        }
        return remote
    }

    @Test
    fun staleDisconnectCannotClearReplacementButCurrentDisconnectClearsSession() = runBlocking {
        connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("old", "chat")
        repository.observe("old")
        val oldConnection = connections.single()
        repository.closeSession(repository.displaySession.value!!.id)
        repository.finishExecution("old")
        repository.registerExecution("new", "chat")
        repository.observe("new")
        val currentId = repository.displaySession.value!!.id
        oldConnection.onServiceDisconnected(mockk())
        assertEquals(currentId, repository.displaySession.value!!.id)
        connections.last().onServiceDisconnected(mockk())
        assertEquals(null, repository.displaySession.value)
        assertEquals("session_closed", repository.observe("new").status)
        repository.setEnabled(false)
    }

    @Test
    fun rejectedAndUnavailableInputReportFailureWhileDeadBinderClearsSession() = runBlocking {
        val remote = connectRemote()
        repository.setEnabled(true)
        repository.registerExecution("turn", "chat")
        repository.observe("turn")
        val id = repository.displaySession.value!!.id
        val down = MobileTouch(MobileTouchAction.DOWN, 10f, 20f, 100, 100)
        every { remote.touch(any(), any(), any(), any(), any(), any()) } returns false
        assertEquals("input_unavailable", repository.manualTouch(id, down).status)
        assertEquals("input_rejected", repository.manualTouch(id, down.copy(action = MobileTouchAction.CANCEL)).status)
        every { remote.touch(any(), any(), any(), any(), any(), any()) } throws RemoteException()
        assertEquals("input_unavailable", repository.manualTouch(id, down).status)
        assertEquals(id, repository.displaySession.value!!.id)
        every { remote.touch(any(), any(), any(), any(), any(), any()) } throws DeadObjectException()
        assertEquals("session_lost", repository.manualTouch(id, down).status)
        assertEquals(null, repository.displaySession.value)
        assertEquals("session_closed", repository.observe("turn").status)
        repository.setEnabled(false)
    }
}
