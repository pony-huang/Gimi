package github.ponyhuang.gimi.mobileuse

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 本机窗口形态，完全独立于 AI 执行状态。 */
enum class BackgroundAppPresentation { HIDDEN, BUBBLE, FULLSCREEN, SMALL_WINDOW }

/** app 组合窗口与独立前台服务；无悬浮权限仍保留应用内入口。 */
@Singleton
class BackgroundAppWindowHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MobileUseRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutablePresentation = MutableStateFlow(BackgroundAppPresentation.HIDDEN)
    val presentation = mutablePresentation.asStateFlow()
    private val mutableOverlayRunning = MutableStateFlow(false)
    val overlayRunning = mutableOverlayRunning.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            var lastId: String? = null
            repository.displaySession.collect { session ->
                if (session?.id != lastId) {
                    lastId = session?.id
                    mutablePresentation.value = if (session == null) BackgroundAppPresentation.HIDDEN else BackgroundAppPresentation.BUBBLE
                    if (session != null) ensureService()
                }
            }
        }
    }

    fun canOverlay(): Boolean = Settings.canDrawOverlays(context)
    fun activateFullscreen(sessionId: String) {
        if (repository.displaySession.value?.id == sessionId) mutablePresentation.value = BackgroundAppPresentation.FULLSCREEN
    }
    fun setOverlayRunning(running: Boolean) { mutableOverlayRunning.value = running }

    fun ensureService(): Boolean {
        if (repository.displaySession.value == null) return false
        return try {
            ContextCompat.startForegroundService(context, Intent(context, BackgroundAppWindowService::class.java))
            true
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // 从后台新建时可能受系统限制；返回 Gimi 时重试，应用内气泡仍可用。
            mutableOverlayRunning.value = false
            Log.w("BackgroundAppWindow", "Service start deferred until app is visible")
            false
        } catch (_: SecurityException) {
            mutableOverlayRunning.value = false
            false
        }
    }

    fun open() {
        val session = repository.displaySession.value ?: return
        val serviceAvailable = ensureService()
        if (repository.smallWindowEnabled.value && canOverlay() && serviceAvailable) {
            mutablePresentation.value = BackgroundAppPresentation.SMALL_WINDOW
        } else {
            mutablePresentation.value = BackgroundAppPresentation.FULLSCREEN
            context.startActivity(Intent(context, BackgroundAppActivity::class.java)
                .putExtra(SESSION_ID, session.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    fun collapse() {
        mutablePresentation.value = if (repository.displaySession.value == null) BackgroundAppPresentation.HIDDEN else BackgroundAppPresentation.BUBBLE
        ensureService()
    }

    fun switchWindow(small: Boolean) {
        scope.launch {
            repository.setSmallWindowEnabled(small)
            open()
        }
    }

    companion object { const val SESSION_ID = "background_app_session_id" }
}
