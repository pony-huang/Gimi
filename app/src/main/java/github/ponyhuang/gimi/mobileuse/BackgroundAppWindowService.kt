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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppViewModel
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
    private var appliedBounds: List<Int>? = null

    override fun onCreate() {
        super.onCreate()
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        windowContext = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        windowManager = windowContext.getSystemService(WindowManager::class.java)
        scope.launch {
            // 非焦点悬浮窗的局部 IME insets 可能为零，按主屏指标同步键盘可用区域。
            while (true) {
                delay(250)
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
        val layout = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
                        detectDragGestures(onDragEnd = { if (!small) snapBubble() }) { change, delta ->
                            change.consume()
                            moveWindow(delta.x.toInt(), delta.y.toInt())
                        }
                    }
                    if (small) BackgroundAppContent(viewModel, gateway, host, small = true, modifier = Modifier.fillMaxSize(), toolbarModifier = dragModifier)
                    else BackgroundAppBubble(onOpen = host::open, modifier = dragModifier)
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
        } catch (_: SecurityException) { removeWindow() }
        catch (_: WindowManager.BadTokenException) { removeWindow() }
    }

    private fun moveWindow(dx: Int, dy: Int) {
        params?.let { it.x += dx; it.y += dy }
        updateBounds()
    }

    private fun snapBubble() {
        val layout = params ?: return
        layout.x = if (layout.x + layout.width / 2 < windowManager.currentWindowMetrics.bounds.width() / 2) 0 else windowManager.currentWindowMetrics.bounds.width() - layout.width
        updateBounds()
    }

    private fun updateBounds() {
        val view = root ?: return
        val layout = params ?: return
        val metrics = windowManager.currentWindowMetrics
        val safe = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.systemGestures())
        val mainScreenKeyboard = metrics.windowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
        val bottom = maxOf(safe.bottom, keyboardBottom, mainScreenKeyboard)
        val availableHeight = (metrics.bounds.height() - safe.top - bottom).coerceAtLeast(dp(56))
        if (shown == BackgroundAppPresentation.SMALL_WINDOW) {
            layout.width = minOf(dp(320), (metrics.bounds.width() - safe.left - safe.right - dp(16)).coerceAtLeast(dp(160)))
            layout.height = minOf(dp(620), availableHeight - dp(16)).coerceAtLeast(dp(56))
        }
        layout.x = layout.x.coerceIn(safe.left, maxOf(safe.left, metrics.bounds.width() - safe.right - layout.width))
        layout.y = layout.y.coerceIn(safe.top, maxOf(safe.top, metrics.bounds.height() - bottom - layout.height))
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
        root?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
            view.disposeComposition()
        }
        root = null; params = null; shownSession = null; shown = BackgroundAppPresentation.HIDDEN
        appliedBounds = null
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
