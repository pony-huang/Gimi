package github.ponyhuang.gimi.environment.runtime

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView

/** 预登记操作入口，以及由同签名宿主显式发起的前台恢复入口。 */
class RuntimeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { setText(R.string.runtime_name) })
        // pendingOperation 由宿主经 Provider（signature 权限）写入；无登记时（如 Launcher 点开）直接退出。
        val engine = RuntimeEngine.get(this)
        val operation = if (intent.action == ACTION_RECOVER) {
            // for-result 启动的 callingPackage 由系统提供，恢复不依赖已被后台策略拦截的 Provider。
            val caller = callingPackage
            if (caller == null || packageManager.checkSignatures(caller, packageName) != PackageManager.SIGNATURE_MATCH) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }
            engine.takePendingOperation()
            if (engine.call("status", Bundle()).getBoolean("installed")) "enable" else "connect"
        } else engine.takePendingOperation() ?: run { finish(); return }
        try {
            startForegroundService(Intent(this, RuntimeService::class.java).setAction(operation))
            setResult(RESULT_OK)
        } catch (_: IllegalStateException) {
            setResult(RESULT_CANCELED)
        } catch (_: SecurityException) {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    companion object {
        const val ACTION_RECOVER = "github.ponyhuang.gimi.environment.RECOVER"
    }
}
