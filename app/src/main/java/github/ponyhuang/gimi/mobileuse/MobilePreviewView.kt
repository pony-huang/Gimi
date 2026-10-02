package github.ponyhuang.gimi.mobileuse

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import github.ponyhuang.gimi.data.mobileuse.MobileDisplayPreviewGateway
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplayCoordinates
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplaySession
import github.ponyhuang.gimi.domain.mobileuse.MobileTouch
import github.ponyhuang.gimi.domain.mobileuse.MobileTouchAction
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppAction
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android Surface 宿主与触摸映射；几何/Surface 改变首先结束未完成手势。 */
internal class MobilePreviewView(
    context: Context,
    private val gateway: MobileDisplayPreviewGateway,
    session: MobileDisplaySession,
    private val onAction: (BackgroundAppAction) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bindingMutex = Mutex()
    @Volatile private var session = session
    private val bindingId = UUID.randomUUID().toString()
    @Volatile private var output: Surface? = null
    private var gesture: Long? = null
    private var x = 0f
    private var y = 0f

    init { surfaceTextureListener = this; isFocusable = false; isOpaque = true }

    fun updateSession(next: MobileDisplaySession) {
        if (session.width != next.width || session.height != next.height) cancelGesture()
        session = next
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        output = Surface(texture)
        bind(width, height)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        cancelGesture()
        bind(width, height)
    }

    private fun bind(width: Int, height: Int) {
        val surface = output ?: return
        val id = session.id
        scope.launch {
            bindingMutex.withLock {
                if (output !== surface || !surface.isValid) return@withLock
                val result = gateway.attachPreview(id, bindingId, surface, width, height)
                if (output === surface && session.id == id) {
                    onAction(BackgroundAppAction.PreviewFailure(result.status != "preview_attached"))
                }
            }
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        cancelGesture()
        val surface = output
        output = null
        val id = session.id
        scope.launch {
            bindingMutex.withLock {
                try {
                    gateway.detachPreview(id, bindingId)
                } finally {
                    surface?.release()
                }
            }
        }
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            cancelGesture()
            return true
        }
        val point = MobileDisplayCoordinates.map(event.x, event.y, width, height, session.width, session.height)
        if (point == null) { cancelGesture(); return true }
        x = point.x; y = point.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelGesture()
                gesture = event.downTime
                send(MobileTouchAction.DOWN, event.eventTime)
            }
            MotionEvent.ACTION_MOVE -> send(MobileTouchAction.MOVE, event.eventTime)
            MotionEvent.ACTION_UP -> { send(MobileTouchAction.UP, event.eventTime); gesture = null }
        }
        return true
    }

    fun cancelGesture() {
        send(MobileTouchAction.CANCEL, SystemClock.uptimeMillis())
        gesture = null
    }

    private fun send(action: MobileTouchAction, time: Long) {
        val id = gesture ?: return
        onAction(BackgroundAppAction.Touch(session.id, MobileTouch(action, x, y, id, time)))
    }

    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }
}
