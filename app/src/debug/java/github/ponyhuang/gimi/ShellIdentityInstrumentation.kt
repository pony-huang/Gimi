package github.ponyhuang.gimi

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.CountDownLatch

/**
 * 仅供 Debug 构建使用的 AppFunctions Shell 身份启动入口。
 *
 * 正式版本不包含此类及其清单声明；开发验证时必须通过 ADB instrumentation
 * 启动，使 Gimi 进程临时具备跨应用发现和执行 AppFunctions 的权限。
 */
class ShellIdentityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        uiAutomation.adoptShellPermissionIdentity(
            "android.permission.EXECUTE_APP_FUNCTIONS",
        )

        val launchIntent = Intent.makeMainActivity(
            ComponentName(targetContext, MainActivity::class.java),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetContext.startActivity(launchIntent)

        // Shell 身份只在 instrumentation 存活期间有效，退出会立即恢复普通应用权限。
        try {
            CountDownLatch(1).await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
