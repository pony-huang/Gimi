package github.ponyhuang.gimi.environment.runtime

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuntimeRecoveryActivityTest {
    private val caller = "github.ponyhuang.gimi.debug"
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        File(app.filesDir, "installed").delete()
        // 单例的 Context 归属应用进程，测试间重建进程态以隔离文件夹。
        RuntimeEngine::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
    }

    private fun registerCaller(matching: Boolean) {
        val own = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
        own.signatures = arrayOf(Signature("1234"))
        shadowOf(app.packageManager).installPackage(own)
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = caller
            signatures = arrayOf(Signature(if (matching) "1234" else "5678"))
        })
    }

    private fun recover(from: String?): RuntimeActivity {
        val controller = Robolectric.buildActivity(RuntimeActivity::class.java,
            Intent(app, RuntimeActivity::class.java).setAction(RuntimeActivity.ACTION_RECOVER))
        shadowOf(controller.get()).setCallingPackage(from)
        controller.create()
        return controller.get()
    }

    @Test
    fun callerWithoutForResultIdentityCannotRecover() {
        val activity = recover(null)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun differentSignatureCannotStartForegroundService() {
        registerCaller(false)
        val activity = recover(caller)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun trustedRecoveryEnablesExistingEnvironment() {
        registerCaller(true)
        File(app.filesDir, "installed").writeText("debian")
        val activity = recover(caller)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals("enable", shadowOf(app).nextStartedService.action)
        assertEquals("debian", File(app.filesDir, "installed").readText())
    }

    @Test
    fun trustedRecoveryWithoutRootfsOnlyConnects() {
        registerCaller(true)
        val activity = recover(caller)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals("connect", shadowOf(app).nextStartedService.action)
        assertFalse(File(app.filesDir, "installed").exists())
    }
}
