package github.ponyhuang.gimi.mobileuse

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.session?.id) { if (state.session == null) onCollapsed() }
    BackgroundAppScreen(state, small, viewModel::onAction,
        onCollapse = { host.collapse(); onCollapsed() },
        onSwitchWindow = { host.switchWindow(!small); if (!small && host.canOverlay()) onCollapsed() },
        onOverlayPermission = {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        frame = { frameModifier ->
            state.session?.let { session ->
                key(session.id) {
                    AndroidView(modifier = frameModifier,
                        factory = { MobilePreviewView(it, gateway, session, viewModel::onAction) },
                        update = { it.updateSession(session) },
                        onRelease = { it.cancelGesture() })
                }
            }
        }, modifier = modifier, toolbarModifier = toolbarModifier,
        dialogHost = { dismiss, content ->
            if (small) BackgroundAppOverlayDialog(dismiss, content)
            else Dialog(onDismissRequest = dismiss, content = content)
        })
}
