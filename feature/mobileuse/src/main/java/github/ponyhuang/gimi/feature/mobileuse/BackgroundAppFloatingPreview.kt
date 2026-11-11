package github.ponyhuang.gimi.feature.mobileuse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplaySession
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 蓝色圆角实时预览；缩略窗只读，放大窗通过顶部横杆展开缩小和结束会话操作。 */
@Composable
fun BackgroundAppFloatingPreview(
    state: BackgroundAppUiState,
    compact: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onAction: (BackgroundAppAction) -> Unit,
    frame: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(if (compact) 16.dp else 24.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        Box(Modifier.fillMaxSize()) {
            if (state.session != null) frame(Modifier.fillMaxSize())
            if (state.session == null || state.previewUnavailable) {
                Text(stringResource(if (state.session == null) R.string.background_app_disconnected
                    else R.string.background_app_preview_unavailable),
                    modifier = Modifier.align(Alignment.Center).padding(if (compact) 8.dp else 24.dp),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium)
            }
            if (compact) {
                val expandDescription = stringResource(R.string.background_app_expand_preview)
                // 拖动越过阈值后消费事件，抬手不会误触发放大，也不会操作真实副屏。
                Box(Modifier.matchParentSize().then(dragModifier)
                    .semantics { contentDescription = expandDescription }
                    .combinedClickable(role = Role.Button,
                        onClickLabel = stringResource(R.string.background_app_expand_preview),
                        onLongClickLabel = stringResource(R.string.background_app_more),
                        onLongClick = { onAction(BackgroundAppAction.Menu(!state.menuExpanded)) },
                        onClick = onExpand))
                if (state.menuExpanded) {
                    Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                        Row {
                            IconButton(onClick = { onAction(BackgroundAppAction.Menu(false)); onExpand() }) {
                                Icon(Icons.Outlined.OpenInFull, stringResource(R.string.background_app_expand_preview))
                            }
                            IconButton(onClick = { onAction(BackgroundAppAction.Close) }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.background_app_close))
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) {
                    if (state.menuExpanded) {
                        Surface(Modifier.then(dragModifier), shape = RoundedCornerShape(24.dp),
                            shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)) {
                            Row {
                                IconButton(onClick = { onAction(BackgroundAppAction.Menu(false)); onCollapse() }) {
                                    Icon(Icons.Outlined.HorizontalRule, stringResource(R.string.background_app_small_window))
                                }
                                IconButton(onClick = { onAction(BackgroundAppAction.Menu(false)); onAction(BackgroundAppAction.Close) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.background_app_close))
                                }
                                IconButton(onClick = { onAction(BackgroundAppAction.Menu(false)) }) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.background_app_hide_controls))
                                }
                            }
                        }
                    } else {
                        val description = stringResource(R.string.background_app_more)
                        // 横杆先交给宿主识别拖动；普通点击仅展开菜单，不向副屏注入触摸。
                        Box(Modifier.size(64.dp, 32.dp).then(dragModifier)
                            .semantics { contentDescription = description }
                            .clickable(role = Role.Button) { onAction(BackgroundAppAction.Menu(true)) },
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(40.dp, 4.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), CircleShape)
                                .border(0.5.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "实时缩略窗 · 浅色", widthDp = 112, heightDp = 236)
@Composable
private fun FloatingCompactLightPreview() = FloatingPreviewFixture(false, true)

@Preview(name = "实时缩略窗 · 深色", widthDp = 112, heightDp = 236)
@Composable
private fun FloatingCompactDarkPreview() = FloatingPreviewFixture(true, true)

@Preview(name = "放大预览 · 浅色", widthDp = 264, heightDp = 540)
@Composable
private fun FloatingExpandedLightPreview() = FloatingPreviewFixture(false, false)

@Preview(name = "放大预览 · 深色", widthDp = 264, heightDp = 540)
@Composable
private fun FloatingExpandedDarkPreview() = FloatingPreviewFixture(true, false)

@Preview(name = "放大预览控制 · 浅色", widthDp = 264, heightDp = 540)
@Composable
private fun FloatingExpandedControlsLightPreview() = FloatingPreviewFixture(false, false, menu = true)

@Preview(name = "放大预览控制 · 深色", widthDp = 264, heightDp = 540)
@Composable
private fun FloatingExpandedControlsDarkPreview() = FloatingPreviewFixture(true, false, menu = true)

@Preview(name = "横屏缩略窗", widthDp = 208, heightDp = 116)
@Composable
private fun FloatingLandscapePreview() = FloatingPreviewFixture(false, true, landscape = true)

@Preview(name = "缩略窗画面不可用", widthDp = 112, heightDp = 236)
@Composable
private fun FloatingUnavailablePreview() = FloatingPreviewFixture(false, true, unavailable = true)

@Composable
private fun FloatingPreviewFixture(dark: Boolean, compact: Boolean, landscape: Boolean = false, unavailable: Boolean = false, menu: Boolean = false) {
    GimiTheme(darkTheme = dark) {
        BackgroundAppFloatingPreview(BackgroundAppUiState(
            session = MobileDisplaySession("preview", "chat", 1, if (landscape) 2400 else 1080, if (landscape) 1080 else 2400),
            previewUnavailable = unavailable,
            menuExpanded = menu,
        ), compact, {}, {}, {}, frame = { modifier ->
            Box(modifier.padding(12.dp)) { Text("实时应用画面", style = MaterialTheme.typography.labelSmall) }
        }, modifier = Modifier.fillMaxSize())
    }
}
