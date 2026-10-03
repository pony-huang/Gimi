package github.ponyhuang.gimi.mobileuse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.IBinder
import android.view.Gravity
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.R
import github.ponyhuang.gimi.data.mobileuse.MobileDisplayPreviewGateway
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppBubble
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppBubbleBounds
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppBubbleMotionState
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppWindowBounds
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppWindowGeometry
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppViewModel
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** 显示会话的独立前台服务；不读取 Agent 执行状态，用户可随时关闭。 */
@AndroidEntryPoint
class BackgroundAppWindowService : Service() {
    @Inject lateinit var repository: MobileUseRepository
    @Inject lateinit var gateway: MobileDisplayPreviewGateway
    @Inject lateinit var host: BackgroundAppWindowHost
    @Inject lateinit var appearance: AppearanceRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var windowContext: Context
    private lateinit var owner: OverlayOwners
    private lateinit var viewModel: BackgroundAppViewModel
    private var root: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var shown: BackgroundAppPresentation = BackgroundAppPresentation.HIDDEN
    private var shownSession: String? = null
    private var keyboardBottom = 0
    private var notifiedSession: String? = null
    private var bubbleMotion: BackgroundAppBubbleMotionState? = null
    private var bubblePositionJob: Job? = null
    private var appliedBounds: List<Int>? = null
    private var smallWindowBounds: BackgroundAppWindowBounds? = null

    override fun onCreate() {
        super.onCreate()
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        windowContext = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        windowManager = windowContext.getSystemService(WindowManager::class.java)
        scope.launch {
            // 非焦点悬浮窗的局部 IME insets 可能为零，按主屏指标同步键盘可用区域。
            while (true) {
                delay(250.milliseconds)
                if (root != null) updateBounds()
            }
        }
        owner = OverlayOwners()
        // app 组合根为服务提供 ViewModelStore；业务状态仍只依赖领域仓库。
        viewModel = androidx.lifecycle.ViewModelProvider(owner, object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BackgroundAppViewModel(repository) as T
            }
        })[BackgroundAppViewModel::class.java]
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.background_app_channel), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        scope.launch {
            combine(repository.displaySession, host.presentation) { session, presentation -> session to presentation }.collect { (session, presentation) ->
                if (session == null) { removeWindow(); stopSelf(); return@collect }
                if (notifiedSession != session.id) {
                    notifiedSession = session.id
                    manager.notify(NOTIFICATION, notification())
                }
                if (!host.canOverlay() || presentation == BackgroundAppPresentation.FULLSCREEN || presentation == BackgroundAppPresentation.HIDDEN) {
                    removeWindow()
                    return@collect
                }
                if (session.id != shownSession || presentation != shown || root == null) {
                    removeWindow()
                    showWindow(presentation, session.id)
                } else updateBounds()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CLOSE) {
            intent.getStringExtra(BackgroundAppWindowHost.SESSION_ID)?.let { id -> scope.launch { repository.closeSession(id) } }
        }
        return START_NOT_STICKY // 没有跨进程恢复；新进程不得创建空白显示或占用。
    }

    private fun notification(): Notification {
        val session = repository.displaySession.value
        val open = Intent(this, BackgroundAppActivity::class.java).putExtra(BackgroundAppWindowHost.SESSION_ID, session?.id)
        val close = Intent(this, BackgroundAppWindowService::class.java).setAction(CLOSE)
            .setData(Uri.parse("gimi://background-app/${session?.id}/close"))
            .putExtra(BackgroundAppWindowHost.SESSION_ID, session?.id)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.background_app_notification)).setContentText(getString(R.string.background_app_notification_detail))
            .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(null, getString(R.string.background_app_notification_close),
                PendingIntent.getService(this, 1, close, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
            .setOngoing(true).setOnlyAlertOnce(true).build()
    }

    private fun showWindow(presentation: BackgroundAppPresentation, sessionId: String) {
        shown = presentation
        shownSession = sessionId
        val small = presentation == BackgroundAppPresentation.SMALL_WINDOW
        val metrics = windowManager.currentWindowMetrics
        val bubbleSize = dp(56)
        val width = if (small) minOf(dp(320), metrics.bounds.width() - dp(16)) else bubbleSize
        val height = if (small) minOf(dp(620), (metrics.bounds.height() * .76f).toInt()) else bubbleSize
        if (small && host.smallWindowGeometry == null) {
            val session = repository.displaySession.value ?: return
            val ratio = session.width.toFloat() / session.height
            host.smallWindowGeometry = BackgroundAppWindowGeometry(
                left = (metrics.bounds.width() - width) / 2f,
                top = (metrics.bounds.height() - height) / 2f,
                width = width.toFloat(), aspectRatio = ratio, chromeHeight = dp(112).toFloat(),
            )
        }
        val layout = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (!small) WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else 0),
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (small) (metrics.bounds.width() - width) / 2 else metrics.bounds.width() - width
            y = (metrics.bounds.height() - height) / 2
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        params = layout
        val view = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val mode by appearance.themeMode.collectAsStateWithLifecycle()
                val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
                AsssistantaiTheme(darkTheme = dark) {
                    val dragModifier = Modifier.pointerInput(presentation) {
                        detectDragGestures(
                            onDragStart = { if (small) beginWindowGesture() else bubbleMotion?.beginDrag() },
                            onDragCancel = { if (!small) bubbleMotion?.endDrag() },
                            onDragEnd = { if (!small) bubbleMotion?.endDrag() },
                        ) { change, delta ->
                            change.consume()
                            moveWindow(delta)
                        }
                    }
                    val resizeModifier = Modifier.pointerInput(presentation) {
                        detectDragGestures(onDragStart = { beginWindowGesture() }) { change, delta ->
                            change.consume()
                            resizeWindow(delta)
                        }
                    }
                    if (small) BackgroundAppContent(viewModel, gateway, host, small = true, modifier = Modifier.fillMaxSize(),
                        toolbarModifier = dragModifier, resizeModifier = resizeModifier)
                    else BackgroundAppBubble(onOpen = host::open, icon = { BackgroundAppIcon() }, modifier = dragModifier)
                }
            }
        }
        root = view
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            keyboardBottom = if (insets.isVisible(WindowInsetsCompat.Type.ime())) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
            view.post { if (root === view) updateBounds() }
            insets
        }
        try {
            windowManager.addView(view, layout)
            host.setOverlayRunning(true)
            updateBounds()
            bubbleMotion?.let { motion ->
                bubblePositionJob = scope.launch {
                    snapshotFlow { motion.position }.collect {
                        if (bubbleMotion === motion && params === layout) applyBubblePosition()
                    }
                }
            }
        } catch (_: SecurityException) { removeWindow() }
        catch (_: WindowManager.BadTokenException) { removeWindow() }
    }

    private fun moveWindow(delta: Offset) {
        if (shown == BackgroundAppPresentation.BUBBLE) {
            // 保留亚像素累计位移，慢速拖动也不能因为逐次取整而卡住。
            bubbleMotion?.dragBy(delta)
            applyBubblePosition()
        } else {
            val bounds = smallWindowBounds ?: return
            host.smallWindowGeometry = host.smallWindowGeometry?.moveBy(delta.x, delta.y, bounds)
            updateBounds()
        }
    }

    private fun beginWindowGesture() {
        val layout = params ?: return
        // 从当前可见几何开始手势，键盘曾压缩窗口时也不会突然跳回屏外。
        host.smallWindowGeometry = host.smallWindowGeometry?.copy(
            left = layout.x.toFloat(), top = layout.y.toFloat(), width = layout.width.toFloat(),
        )
    }

    private fun resizeWindow(delta: Offset) {
        if (shown != BackgroundAppPresentation.SMALL_WINDOW) return
        val bounds = smallWindowBounds ?: return
        host.smallWindowGeometry = host.smallWindowGeometry?.resizeBy(delta.x, delta.y, bounds)
        updateBounds()
    }

    private fun applyBubblePosition() {
        val layout = params ?: return
        val motion = bubbleMotion ?: return
        layout.x = motion.position.x.roundToInt()
        layout.y = motion.position.y.roundToInt()
        // 动画帧只提交坐标；屏幕指标与系统 insets 继续由原来的监听和轮询更新。
        applyWindowBounds()
    }

    private fun updateBounds() {
        if (root == null) return
        val layout = params ?: return
        val metrics = windowManager.currentWindowMetrics
        val safe = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.systemGestures())
        val mainScreenKeyboard = metrics.windowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
        val bottom = maxOf(safe.bottom, keyboardBottom, mainScreenKeyboard)
        if (shown == BackgroundAppPresentation.SMALL_WINDOW) {
            val bounds = BackgroundAppWindowBounds(
                left = (safe.left + dp(8)).toFloat(), top = (safe.top + dp(8)).toFloat(),
                right = (metrics.bounds.width() - safe.right - dp(8)).toFloat(),
                bottom = (metrics.bounds.height() - bottom - dp(8)).toFloat(), minWidth = dp(280).toFloat(),
            )
            smallWindowBounds = bounds
            val session = repository.displaySession.value ?: return
            val requested = host.smallWindowGeometry?.copy(
                aspectRatio = session.width.toFloat() / session.height, chromeHeight = dp(112).toFloat(),
            ) ?: return
            // 安全区域约束只改变可见尺寸，键盘关闭后恢复用户选择的尺寸。
            val visible = requested.constrain(bounds)
            layout.width = visible.width.roundToInt()
            layout.height = visible.height.roundToInt()
            layout.x = visible.left.roundToInt()
            layout.y = visible.top.roundToInt()
        }
        if (shown == BackgroundAppPresentation.BUBBLE) {
            val motion = bubbleMotion ?: BackgroundAppBubbleMotionState(
                // Service 自身没有 Compose 帧时钟；使用主线程 Choreographer 同步窗口动画。
                CoroutineScope(scope.coroutineContext + AndroidUiDispatcher.Main),
                Offset(layout.x.toFloat(), layout.y.toFloat()),
            ).also { bubbleMotion = it }
            motion.updateBounds(BackgroundAppBubbleBounds(
                left = 0f,
                right = (metrics.bounds.width() - layout.width).coerceAtLeast(0).toFloat(),
                top = safe.top.toFloat(),
                bottom = maxOf(safe.top, metrics.bounds.height() - bottom - layout.height).toFloat(),
                halfSize = layout.width / 2f,
            ))
            layout.x = motion.position.x.roundToInt()
            layout.y = motion.position.y.roundToInt()
        } else {
            layout.x = layout.x.coerceIn(safe.left, maxOf(safe.left, metrics.bounds.width() - safe.right - layout.width))
            layout.y = layout.y.coerceIn(safe.top, maxOf(safe.top, metrics.bounds.height() - bottom - layout.height))
        }
        applyWindowBounds()
    }

    private fun applyWindowBounds() {
        val view = root ?: return
        val layout = params ?: return
        val bounds = listOf(layout.x, layout.y, layout.width, layout.height)
        // 输入法 insets 与窗口布局互相回调，只在几何确实变化时提交新布局。
        if (view.isAttachedToWindow && appliedBounds != bounds) {
            appliedBounds = bounds
            windowManager.updateViewLayout(view, layout)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); updateBounds() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun removeWindow() {
        bubblePositionJob?.cancel()
        bubblePositionJob = null
        bubbleMotion?.stop()
        bubbleMotion = null
        root?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
            view.disposeComposition()
        }
        root = null; params = null; shownSession = null; shown = BackgroundAppPresentation.HIDDEN
        appliedBounds = null
        smallWindowBounds = null
        keyboardBottom = 0
        host.setOverlayRunning(false)
    }

    override fun onDestroy() {
        removeWindow()
        owner.close()
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL = "background_app_window"
        const val NOTIFICATION = 2817
        const val CLOSE = "github.ponyhuang.gimi.CLOSE_BACKGROUND_APP"
    }
}

/** 每个悬浮服务独立的 Compose 生命周期，销毁时取消 ViewModel 输入队列。 */
private class OverlayOwners : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry = savedState.savedStateRegistry
    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }
    fun close() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
}
