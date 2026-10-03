package github.ponyhuang.gimi.feature.mobileuse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog

/** 已批准的画面窗口：窄工具栏之外全部交给同一个应用画面，打开即可触摸。 */
@Composable
fun BackgroundAppScreen(
    state: BackgroundAppUiState,
    smallWindow: Boolean,
    onAction: (BackgroundAppAction) -> Unit,
    onCollapse: () -> Unit,
    onSwitchWindow: () -> Unit,
    onOverlayPermission: () -> Unit,
    frame: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    toolbarModifier: Modifier = Modifier,
    resizeModifier: Modifier = Modifier,
    dialogHost: @Composable (onDismiss: () -> Unit, content: @Composable () -> Unit) -> Unit = { dismiss, content ->
        Dialog(onDismissRequest = dismiss, content = content)
    },
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(if (smallWindow) 24.dp else 0.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = if (smallWindow) 8.dp else 0.dp) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // 拖动入口与窗口按钮分开，应用名称不再让用户误以为这里可以点击。
                Row(modifier = Modifier.weight(1f).height(56.dp).then(toolbarModifier), verticalAlignment = Alignment.CenterVertically) {
                    if (smallWindow) {
                        Icon(Icons.Outlined.PanTool, contentDescription = stringResource(R.string.background_app_drag),
                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.background_app_drag_hint),
                            modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.HorizontalRule, contentDescription = stringResource(R.string.background_app_collapse))
                }
                IconButton(onClick = onSwitchWindow, modifier = Modifier.size(48.dp)) {
                    Icon(if (smallWindow) Icons.Outlined.OpenInFull else Icons.Outlined.CloseFullscreen,
                        contentDescription = stringResource(if (smallWindow) R.string.background_app_fullscreen else R.string.background_app_small_window))
                }
                Box {
                    IconButton(onClick = { onAction(BackgroundAppAction.Menu(true)) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.background_app_more))
                    }
                    DropdownMenu(expanded = state.menuExpanded, onDismissRequest = { onAction(BackgroundAppAction.Menu(false)) }) {
                        DropdownMenuItem(text = { Text(stringResource(if (smallWindow) R.string.background_app_fullscreen else R.string.background_app_small_window)) },
                            onClick = { onAction(BackgroundAppAction.Menu(false)); onSwitchWindow() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.background_app_window_settings)) },
                            onClick = { onAction(BackgroundAppAction.Settings(true)) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.background_app_close)) },
                            onClick = { onAction(BackgroundAppAction.Close) })
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                if (state.session != null) frame(Modifier.fillMaxSize())
                if (state.session == null || state.previewUnavailable) {
                    Text(stringResource(if (state.session == null) R.string.background_app_disconnected else R.string.background_app_preview_unavailable),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAction(BackgroundAppAction.Back) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.background_app_back), modifier = Modifier.padding(start = 8.dp))
                }
                Box(Modifier.weight(1f))
                if (smallWindow) {
                    Text(stringResource(R.string.background_app_resize_hint), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.size(48.dp).then(resizeModifier), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.SouthEast, contentDescription = stringResource(R.string.background_app_resize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (state.settingsVisible) {
        dialogHost({ onAction(BackgroundAppAction.Settings(false)) }) {
            BackgroundAppDialogContent(
                title = stringResource(R.string.background_app_window_settings),
                onConfirm = { onAction(BackgroundAppAction.Settings(false)) },
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.background_app_default_small))
                            Text(stringResource(R.string.background_app_default_small_detail), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = state.smallWindowEnabled, onCheckedChange = { onAction(BackgroundAppAction.SmallWindow(it)) })
                    }
                    TextButton(onClick = onOverlayPermission) { Text(stringResource(R.string.background_app_overlay_permission)) }
                }
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
