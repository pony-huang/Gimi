package github.ponyhuang.gimi.feature.mobileuse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** 应用画面铺满窗口；小窗只读，全屏允许操作，窗口控制均由顶部横杆展开。 */
@Composable
fun BackgroundAppScreen(
    state: BackgroundAppUiState,
    smallWindow: Boolean,
    onAction: (BackgroundAppAction) -> Unit,
    onCollapse: () -> Unit,
    onSwitchWindow: () -> Unit,
    frame: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    toolbarModifier: Modifier = Modifier,
    dialogHost: @Composable (onDismiss: () -> Unit, content: @Composable () -> Unit) -> Unit = { dismiss, content ->
        Dialog(onDismissRequest = dismiss, content = content)
    },
) {
    Surface(modifier = modifier, shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = if (smallWindow) 8.dp else 0.dp) {
        BackgroundAppViewport(state, smallWindow, onAction, onCollapse, onSwitchWindow, frame, toolbarModifier)
    }
    if (state.rotationUnavailable) {
        dialogHost({ onAction(BackgroundAppAction.DismissRotationError) }) {
            BackgroundAppDialogContent(onConfirm = { onAction(BackgroundAppAction.DismissRotationError) }) {
                Text(stringResource(R.string.background_app_rotation_unavailable))
            }
        }
    }
    if (state.inputUnavailable) {
        dialogHost({ onAction(BackgroundAppAction.DismissError) }) {
            BackgroundAppDialogContent(onConfirm = { onAction(BackgroundAppAction.DismissError) }) {
                Text(stringResource(R.string.background_app_input_unavailable))
            }
        }
    }
}

/** 相同的 Material 3 弹窗内容由全屏或悬浮宿主承载，不创建 Android 窗口。 */
@Composable
private fun BackgroundAppDialogContent(
    onConfirm: () -> Unit,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp)) }
            content()
            TextButton(onClick = onConfirm, modifier = Modifier.align(Alignment.End).padding(top = 16.dp)) {
                Text(stringResource(R.string.background_app_done))
            }
        }
    }
}

/** 拖动由窗口宿主处理；气泡不读取仓库，也不反映 AI 的执行状态。 */
@Composable
fun BackgroundAppBubble(onOpen: () -> Unit, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.background_app_open)
    Surface(modifier = modifier.size(56.dp).semantics { contentDescription = description }.clickable(onClick = onOpen),
        shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
