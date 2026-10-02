package github.ponyhuang.gimi.data.mobileuse

import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.IBinder
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
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
    private val enabled = MutableStateFlow(false)
    private val preferences = mockk<MobileUsePreferences> {
        every { enabled } returns this@ShizukuMobileUseRepositoryTest.enabled
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
            secondArg<ServiceConnection>().onServiceConnected(mockk(), binder)
        }
        every { Shizuku.unbindUserService(any(), any(), true) } returns Unit

        repository.setEnabled(true)
        assertTrue(repository.enabled.value)
        assertEquals("operation_failed", repository.observe("first-task").status)
        assertEquals(MobileUseAvailability.BUSY, repository.availability())
        repository.setEnabled(false)
        assertFalse(repository.enabled.value)
        assertEquals("disabled", repository.observe("first-task").status)
        verify(exactly = 1) { remote.stop() }
        verify(exactly = 1) { Shizuku.unbindUserService(any(), any(), true) }

        repository.setEnabled(true)
        assertEquals(MobileUseAvailability.READY, repository.availability())
        assertEquals("operation_failed", repository.observe("second-task").status)
        verify(exactly = 2) { remote.createDisplay() }
    }
}
