package github.ponyhuang.gimi.mobileuse

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.data.mobileuse.MobileDisplayPreviewGateway
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppScreen
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppViewModel

/** 共用 feature 画面，由 app 组合具体 Android Surface 及窗口切换。 */
@Composable
internal fun BackgroundAppContent(
    viewModel: BackgroundAppViewModel,
    gateway: MobileDisplayPreviewGateway,
    host: BackgroundAppWindowHost,
    small: Boolean,
    modifier: Modifier = Modifier,
    toolbarModifier: Modifier = Modifier,
    onCollapsed: () -> Unit = {},
    floating: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.session?.id) { if (state.session == null) onCollapsed() }
    val frame: @Composable (Modifier) -> Unit = { frameModifier ->
        state.session?.let { session ->
            key(session.id, small) {
                Box(frameModifier) {
                    AndroidView(modifier = Modifier.matchParentSize(),
                        factory = { MobilePreviewView(it, gateway, session, interactive = !small, onAction = viewModel::onAction) },
                        update = { it.updateSession(session) },
                        onRelease = { it.cancelGesture() })
                }
            }
        }
    }
    BackgroundAppScreen(state, small, viewModel::onAction,
        onCollapse = { host.collapse(); onCollapsed() },
        onSwitchWindow = {
            if (floating) host.open()
            else { host.switchWindow(!small); if (!small && host.canOverlay()) onCollapsed() }
        },
        frame = frame, modifier = modifier, toolbarModifier = toolbarModifier, floatingWindow = floating,
        dialogHost = { dismiss, content ->
            if (small || floating) BackgroundAppOverlayDialog(dismiss, content)
            else Dialog(onDismissRequest = dismiss, content = content)
        })
}
