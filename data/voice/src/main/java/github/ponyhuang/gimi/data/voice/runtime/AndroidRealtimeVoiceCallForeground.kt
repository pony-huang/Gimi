package github.ponyhuang.gimi.data.voice.runtime

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallForeground
import javax.inject.Inject

/** 把通话的前台优先级委托给服务；后台启动受限时静默失败，应用重新可见时由控制器补启。 */
class AndroidRealtimeVoiceCallForeground @Inject constructor(
    @ApplicationContext private val context: Context,
) : RealtimeVoiceCallForeground {
    override fun start() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, RealtimeVoiceCallService::class.java))
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // 从后台新建麦克风型前台服务会被系统拒绝；返回应用时 ensureForeground() 重试。
            Log.w(TAG, "Foreground call service start deferred until the app is visible")
        } catch (_: SecurityException) {
            Log.w(TAG, "Foreground call service unavailable")
        }
    }

    private companion object {
        const val TAG = "RealtimeVoiceCallForeground"
    }
}
