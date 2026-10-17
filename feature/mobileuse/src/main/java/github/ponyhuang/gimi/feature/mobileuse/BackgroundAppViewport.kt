package github.ponyhuang.gimi.feature.mobileuse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplaySession
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 画面始终占满宿主；小窗仅提供窗口管理，全屏额外提供副屏返回和旋转。 */
@Composable
internal fun BackgroundAppViewport(
    state: BackgroundAppUiState,
    smallWindow: Boolean,
    onAction: (BackgroundAppAction) -> Unit,
    onCollapse: () -> Unit,
    onSwitchWindow: () -> Unit,
    frame: @Composable (Modifier) -> Unit,
    toolbarModifier: Modifier,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        val compactControls = smallWindow && maxHeight >= 112.dp
        if (state.session != null) frame(Modifier.fillMaxSize())
        if (state.session == null || state.previewUnavailable) {
            Text(stringResource(if (state.session == null) R.string.background_app_disconnected else R.string.background_app_preview_unavailable),
                modifier = Modifier.align(Alignment.Center).padding(24.dp), style = MaterialTheme.typography.bodyMedium)
        }
        // 小窗宿主已经避让系统栏和键盘，不再把主屏 insets 重复加到小窗内容上。
        val controlsModifier = if (smallWindow) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
        val dragModifier = if (smallWindow) toolbarModifier else Modifier
        val collapse = { onAction(BackgroundAppAction.Menu(false)); onCollapse() }
        val switchWindow = { onAction(BackgroundAppAction.Menu(false)); onSwitchWindow() }
        val close = { onAction(BackgroundAppAction.Menu(false)); onAction(BackgroundAppAction.Close) }
        val hideControls = { onAction(BackgroundAppAction.Menu(false)) }
        val horizontalPadding = if (smallWindow) 8.dp else 12.dp
        Box(Modifier.fillMaxSize().then(controlsModifier).padding(top = 4.dp, start = horizontalPadding, end = horizontalPadding)) {
            if (state.menuExpanded) {
                Surface(modifier = Modifier.align(Alignment.TopCenter).then(dragModifier), shape = RoundedCornerShape(if (smallWindow) 24.dp else 28.dp),
                    shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)) {
                    if (compactControls) {
                        // 固定小窗较窄，四个窗口动作排成两行，避免缩小点击目标或隐藏关键按钮。
                        Column(Modifier.padding(4.dp)) {
                            Row {
                                WindowControl(Icons.Outlined.HorizontalRule, stringResource(R.string.background_app_collapse), onClick = collapse)
                                WindowControl(Icons.Outlined.OpenInFull, stringResource(R.string.background_app_fullscreen), onClick = switchWindow)
                            }
                            Row {
                                WindowControl(Icons.Outlined.Close, stringResource(R.string.background_app_close), onClick = close)
                                WindowControl(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.background_app_hide_controls), onClick = hideControls)
                            }
                        }
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (!smallWindow) {
                                WindowControl(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.background_app_back),
                                    enabled = state.session != null, onClick = {
                                        onAction(BackgroundAppAction.Menu(false))
                                        onAction(BackgroundAppAction.Back)
                                    })
                                WindowControl(Icons.Outlined.ScreenRotation, stringResource(
                                    if (state.session?.let { it.width > it.height } == true) R.string.background_app_rotate_portrait
                                    else R.string.background_app_rotate_landscape),
                                    enabled = state.session != null && !state.rotating, onClick = {
                                        onAction(BackgroundAppAction.Menu(false))
                                        onAction(BackgroundAppAction.Rotate)
                                    })
                            }
                            WindowControl(Icons.Outlined.HorizontalRule, stringResource(R.string.background_app_collapse), onClick = collapse)
                            WindowControl(if (smallWindow) Icons.Outlined.OpenInFull else Icons.Outlined.CloseFullscreen,
                                stringResource(if (smallWindow) R.string.background_app_fullscreen else R.string.background_app_small_window), onClick = switchWindow)
                            WindowControl(Icons.Outlined.Close, stringResource(R.string.background_app_close), onClick = close)
                            WindowControl(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.background_app_hide_controls), onClick = hideControls)
                        }
                    }
                }
            } else {
                val description = stringResource(if (smallWindow) R.string.background_app_preview_controls else R.string.background_app_more)
                // 细横杆兼作小窗拖动入口；超过拖动阈值后宿主会消费手势，点击展开不会误触发。
                Box(Modifier.align(Alignment.TopCenter).size(width = 64.dp, height = 24.dp).then(dragModifier)
                    .clip(RoundedCornerShape(12.dp))
                    .semantics { contentDescription = description }
                    .clickable(role = Role.Button) { onAction(BackgroundAppAction.Menu(true)) },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), CircleShape)
                        .border(0.5.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape))
                }
            }
        }
    }
}

/** 顶部控制条只显示图标，以本地化描述提供无障碍语义，触摸目标为 48dp。 */
@Composable
private fun WindowControl(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

@Preview(name = "全屏浅色", widthDp = 412, heightDp = 892)
@Composable
private fun FullscreenLightPreview() = FullscreenPreview(dark = false)

@Preview(name = "全屏深色", widthDp = 412, heightDp = 892)
@Composable
private fun FullscreenDarkPreview() = FullscreenPreview(dark = true)

@Preview(name = "全屏控制", widthDp = 412, heightDp = 892)
@Composable
private fun FullscreenControlsPreview() = FullscreenPreview(dark = false, menu = true)

@Preview(name = "横屏控制", widthDp = 892, heightDp = 412)
@Composable
private fun FullscreenLandscapePreview() = FullscreenPreview(dark = false, menu = true, landscape = true)

@Preview(name = "画面不可用", widthDp = 412, heightDp = 892)
@Composable
private fun FullscreenUnavailablePreview() = FullscreenPreview(dark = false, unavailable = true)

@Composable
private fun FullscreenPreview(dark: Boolean, menu: Boolean = false, landscape: Boolean = false, unavailable: Boolean = false) {
    AsssistantaiTheme(darkTheme = dark) {
        BackgroundAppScreen(
            state = BackgroundAppUiState(
                session = MobileDisplaySession("preview", "chat", 1, if (landscape) 2400 else 1080, if (landscape) 1080 else 2400),
                menuExpanded = menu, previewUnavailable = unavailable,
            ), smallWindow = false, onAction = {}, onCollapse = {}, onSwitchWindow = {},
            frame = { modifier -> Box(modifier.background(MaterialTheme.colorScheme.background)) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "只读小窗浅色", widthDp = 160, heightDp = 300)
@Composable
private fun SmallWindowLightPreview() = SmallWindowPreview(dark = false)

@Preview(name = "只读小窗深色", widthDp = 160, heightDp = 300)
@Composable
private fun SmallWindowDarkPreview() = SmallWindowPreview(dark = true)

@Preview(name = "小窗控制展开", widthDp = 160, heightDp = 300)
@Composable
private fun SmallWindowControlsPreview() = SmallWindowPreview(dark = false, menu = true)

@Preview(name = "横向小窗控制", widthDp = 160, heightDp = 90)
@Composable
private fun SmallWindowLandscapePreview() = SmallWindowPreview(dark = false, menu = true, landscape = true)

@Composable
private fun SmallWindowPreview(dark: Boolean, menu: Boolean = false, landscape: Boolean = false) {
    AsssistantaiTheme(darkTheme = dark) {
        BackgroundAppScreen(
            state = BackgroundAppUiState(
                session = MobileDisplaySession("preview", "chat", 1, if (landscape) 2340 else 1080, if (landscape) 1080 else 2340),
                menuExpanded = menu,
            ), smallWindow = true, onAction = {}, onCollapse = {}, onSwitchWindow = {},
            frame = { modifier -> Box(modifier.background(MaterialTheme.colorScheme.background)) },
            modifier = Modifier.size(width = if (landscape) 160.dp else 143.dp, height = if (landscape) 73.dp else 310.dp),
        )
    }
}

/** 屏幕范围、贴边和键盘避让的静态预览；仅使用假数据。 */
@Preview(name = "自由拖动 · 浅色", widthDp = 412, heightDp = 892)
@Composable
private fun WindowPlacementLightPreview() = WindowPlacementPreview(dark = false)

@Preview(name = "贴边保留拖动入口 · 深色", widthDp = 412, heightDp = 892)
@Composable
private fun WindowPlacementEdgePreview() = WindowPlacementPreview(dark = true, edge = true)

@Preview(name = "键盘弹出临时避让", widthDp = 412, heightDp = 892)
@Composable
private fun WindowPlacementKeyboardPreview() = WindowPlacementPreview(dark = false, keyboard = true)

@Composable
private fun WindowPlacementPreview(dark: Boolean, edge: Boolean = false, keyboard: Boolean = false) {
    AsssistantaiTheme(darkTheme = dark) {
        Surface {
            Box(Modifier.fillMaxSize()) {
                Text("07:30", Modifier.align(Alignment.TopStart).padding(16.dp))
                Text("聊天内容", Modifier.align(Alignment.Center))
                Text("输入消息", Modifier.align(Alignment.BottomStart).padding(24.dp))
                if (keyboard) {
                    Box(Modifier.align(Alignment.BottomCenter).height(300.dp).fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Text("键盘区域", Modifier.align(Alignment.Center))
                    }
                }
                Box(Modifier.offset(x = if (edge) 364.dp else 252.dp,
                    y = if (keyboard) 282.dp else 530.dp).size(160.dp, 310.dp)) {
                    BackgroundAppViewport(
                        state = BackgroundAppUiState(session = MobileDisplaySession("draft", "draft", 1, 1600, 3100)),
                        smallWindow = true,
                        onAction = {}, onCollapse = {}, onSwitchWindow = {},
                        frame = { modifier ->
                            Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
                                Text("副屏画面 · 可拖动", Modifier.align(Alignment.Center))
                            }
                        },
                        toolbarModifier = Modifier,
                    )
                }
            }
        }
    }
}
