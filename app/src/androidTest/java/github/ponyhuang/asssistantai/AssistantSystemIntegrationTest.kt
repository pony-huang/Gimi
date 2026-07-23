package github.ponyhuang.asssistantai

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import github.ponyhuang.asssistantai.assistant.AssistantOverlayActivity
import github.ponyhuang.asssistantai.assistant.AssistantTileService
import github.ponyhuang.asssistantai.assistant.AssistantVoiceInteractionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 系统入口注册与权限约束的 instrumentation 验证（不触发真实模型服务）。 */
@RunWith(AndroidJUnit4::class)
class AssistantSystemIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun overlayActivityAndSystemServicesAreRegistered() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES,
        )
        val activities = packageInfo.activities?.map { it.name }.orEmpty()
        val services = packageInfo.services?.map { it.name }.orEmpty()

        assertTrue(activities.contains(AssistantOverlayActivity::class.java.name))
        assertTrue(services.contains(AssistantVoiceInteractionService::class.java.name))
        assertTrue(services.contains(AssistantTileService::class.java.name))
    }

    @Test
    fun overlayActivityIsSingleTaskAndExcludedFromRecents() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, AssistantOverlayActivity::class.java),
            0,
        )
        assertEquals(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS, info.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
    }

    @Test
    fun appDoesNotRequestOverlayWindowPermission() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = packageInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(permissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
    }

    @Test
    fun assistantRoleRequestIntentResolvesWhenRoleAvailable() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        assertNotNull(roleManager)
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
        assertNotNull(intent)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertNotNull(resolved)
    }

    @Test
    fun tileLaunchIntentTargetsOverlayActivity() {
        val intent = Intent(context, AssistantOverlayActivity::class.java)
            .putExtra(
                AssistantOverlayActivity.EXTRA_INVOCATION_SOURCE,
                AssistantOverlayActivity.SOURCE_TILE,
            )
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertNotNull(resolved)
        assertEquals(
            AssistantOverlayActivity::class.java.name,
            resolved!!.activityInfo.name,
        )
    }
}
