package github.ponyhuang.gimi.voice

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.MainActivity
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.feature.assistant.AssistantSurface
import github.ponyhuang.gimi.feature.assistant.AssistantSurfaceMode
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import javax.inject.Inject
import javax.inject.Singleton

/** 语音服务持有的底部系统悬浮条；权限缺失时 [show] 安静失败。 */
@Singleton
class AssistantOverlayWindow @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: AssistantSessionCoordinator,
    private val appearanceRepository: AppearanceRepository,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owners: OverlayViewTreeOwners? = null

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (view != null) return true
        val treeOwners = OverlayViewTreeOwners().also(OverlayViewTreeOwners::start)
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(treeOwners)
            setViewTreeSavedStateRegistryOwner(treeOwners)
            setViewTreeViewModelStoreOwner(treeOwners)
            setContent {
                val state by coordinator.state.collectAsStateWithLifecycle()
                val darkThemeOverride by appearanceRepository.darkThemeOverride
                    .collectAsStateWithLifecycle()
                AsssistantaiTheme(darkTheme = darkThemeOverride ?: isSystemInDarkTheme()) {
                    Box(
                        modifier = Modifier
                            .safeDrawingPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        AssistantSurface(
                            state = state,
                            mode = AssistantSurfaceMode.OVERLAY,
                            onDismiss = coordinator::hidePresentation,
                            onStop = coordinator::stop,
                            onOpenChat = ::openCurrentChat,
                        )
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
        return runCatching {
            windowManager.addView(composeView, params)
            owners = treeOwners
            view = composeView
            true
        }.getOrElse {
            treeOwners.close()
            false
        }
    }

    fun hide() {
        val activeView = view ?: return
        view = null
        runCatching { windowManager.removeViewImmediate(activeView) }
        owners?.close()
        owners = null
    }

    private fun openCurrentChat() {
        coordinator.hidePresentation()
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_CURRENT_CHAT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }
}

private class OverlayViewTreeOwners : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun close() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
