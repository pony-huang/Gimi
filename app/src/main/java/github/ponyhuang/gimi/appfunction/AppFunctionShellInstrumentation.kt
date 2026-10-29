package github.ponyhuang.gimi.appfunction

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import github.ponyhuang.gimi.MainActivity
import java.util.concurrent.CountDownLatch

/** 由 ADB 显式启动，在会话存续期间为当前构建借用 Shell 的 AppFunction 权限。 */
class AppFunctionShellInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val automation = uiAutomation
        if (automation == null) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("Error", "UiAutomation unavailable; stop other instrumentation sessions first.")
            })
            return
        }
        try {
            automation.adoptShellPermissionIdentity(EXECUTE_APP_FUNCTIONS)
            check(targetContext.checkSelfPermission(EXECUTE_APP_FUNCTIONS) == PackageManager.PERMISSION_GRANTED) {
                "Device did not grant the Shell AppFunction identity."
            }
            sendStatus(0, Bundle().apply { putString("AppFunctionShell", "permission granted") })
            targetContext.startActivity(
                Intent.makeMainActivity(android.content.ComponentName(targetContext, MainActivity::class.java))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            // 会话结束即撤销借用权限；保持 instrumentation 存活才能在界面中继续测试。
            CountDownLatch(1).await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: SecurityException) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("Error", error.message) })
        } catch (error: IllegalStateException) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("Error", error.message) })
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    private companion object {
        const val EXECUTE_APP_FUNCTIONS = "android.permission.EXECUTE_APP_FUNCTIONS"
    }
}
