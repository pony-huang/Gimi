package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 普通抽屉：标题行 + 主体 + 可选底部操作区。
 *
 * 骨架与会话抽屉一致——标题居中、左侧是返回、右侧是操作，主体滚动，底部操作区固定。
 * 高度、拖动与"只有顶部横杆能改高度"的规则由 [GimiSheetBase] 统一保证。
 *
 * @param title 标题。
 * @param navigationIcon 左侧返回图标；为空则不显示。
 * @param trailing 标题行右侧的操作位（[GimiSheetTextAction] / [GimiSheetIconAction]）。
 * @param footer 底部固定操作区。
 * @param content 主体内容，高度由内容自身决定，滚动由调用方实现。
 */
@Composable
fun GimiSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    initialHeight: SheetHeight = GimiSheetDefaults.InitialHeight,
    maxHeight: SheetHeight = GimiSheetDefaults.MaxHeight,
    height: SheetHeight? = null,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    trailing: @Composable BoxScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    GimiSheetBase(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        initialHeight = initialHeight,
        maxHeight = maxHeight,
        height = height,
        header = {
            GimiSheetTitleRow(
                title = title,
                navigationIcon = navigationIcon,
                navigationContentDescription = navigationContentDescription,
                onNavigationClick = onNavigationClick,
                trailing = trailing,
            )
        },
        footer = footer,
        content = content,
    )
}

/**
 * 抽屉标题行：标题居中，左侧可选返回，右侧是操作位。
 *
 * 标题相对**整行宽度**居中，而不是挤在返回图标与右侧操作之间——所以两侧槽位
 * 各自贴住行的两端（一律左返回、右操作），标题在中间。
 *
 * 标题左右各留出与较宽一侧槽位等宽的安全区，长标题不会压到按钮上；安全区左右
 * 对称，某一侧有按钮时标题也不会偏移。
 *
 * 行的水平留白用 [GimiListDefaults.HorizontalPadding]，与抽屉内列表卡片的边距
 * 相同，标题行与下方列表落在同一条竖直栅格线上。
 */
@Composable
fun GimiSheetTitleRow(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    navigationModifier: Modifier = Modifier,
    trailing: @Composable BoxScope.() -> Unit = {},
) {
    val showNavigation = navigationIcon != null && onNavigationClick != null
    val density = LocalDensity.current
    // 两侧槽位的实测宽度：标题按较宽的一侧预留安全区。
    var navigationWidth by remember { mutableStateOf(0.dp) }
    var trailingWidth by remember { mutableStateOf(0.dp) }
    val reserved = maxOf(navigationWidth, trailingWidth)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GimiListDefaults.HorizontalPadding)
            .padding(top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = reserved),
        )
        if (showNavigation) {
            IconButton(
                onClick = onNavigationClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .onSizeChanged { navigationWidth = with(density) { it.width.toDp() } }
                    .then(navigationModifier),
            ) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = navigationContentDescription,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .onSizeChanged { trailingWidth = with(density) { it.width.toDp() } },
        ) {
            trailing()
        }
    }
}

/** 标题行右侧的文字操作。 */
@Composable
fun GimiSheetTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text)
    }
}

/** 标题行右侧的图标操作。 */
@Composable
fun GimiSheetIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}

// ── 预览 ────────────────────────────────────────────────────

/** 预览按抽屉页面的真实底色渲染，展示标题居中与两侧槽位的关系。 */
@Preview(name = "标题行 · 浅色", showBackground = true, widthDp = 390)
@Preview(
    name = "标题行 · 深色",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun GimiSheetTitleRowPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            Column {
                GimiSheetTitleRow(title = "选择模型")
                GimiSheetTitleRow(
                    title = "Deepseek",
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    navigationContentDescription = "返回",
                    onNavigationClick = {},
                )
                GimiSheetTitleRow(
                    title = "选择模型",
                    trailing = {
                        GimiSheetIconAction(
                            icon = Icons.Default.Close,
                            contentDescription = "关闭",
                            onClick = {},
                        )
                    },
                )
            }
        }
    }
}

/** 长标题在自身份额内省略，不会压到两侧的按钮。 */
@Preview(name = "标题行 · 长文本", showBackground = true, widthDp = 390)
@Composable
private fun GimiSheetTitleRowLongTextPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            GimiSheetTitleRow(
                title = "qwen-audio-3.1-realtime-plus-flash 超长标题",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = "返回",
                onNavigationClick = {},
                trailing = {
                    GimiSheetIconAction(
                        icon = Icons.Default.Close,
                        contentDescription = "关闭",
                        onClick = {},
                    )
                },
            )
        }
    }
}
