package github.ponyhuang.gimi.ui.preference

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.components.GimiListDefaults
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.theme.GimiTheme
import github.ponyhuang.gimi.ui.theme.PreferenceCanvasDark
import github.ponyhuang.gimi.ui.theme.PreferenceCanvasLight
import github.ponyhuang.gimi.ui.theme.PreferenceGroupCardDark
import github.ponyhuang.gimi.ui.theme.PreferenceGroupCardLight

/**
 * 设置页画布色：浅色为 One UI 式浅灰画布，深色沿用全局底色。
 * 通过背景亮度判定深浅主题，与两套 ColorScheme 保持自洽，供页面容器与 Scaffold 共用。
 */
@Composable
fun preferenceCanvasColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) PreferenceCanvasDark else PreferenceCanvasLight
}

/**
 * 设置分组卡片底色：浅色纯白、深色为比画布略亮的表面。
 * 供 [PreferenceGroupCard] 与需要同语言卡片的浮层（如聊天抽屉）共用。
 */
@Composable
fun preferenceGroupCardColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) PreferenceGroupCardDark else PreferenceGroupCardLight
}

@Composable
fun PreferencePageContainer(
    modifier: Modifier = Modifier,
    style: PreferenceStyle = LocalPreferenceStyle.current,
    content: @Composable () -> Unit,
) {
    PreferenceStyleScope(style) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(preferenceCanvasColor()),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            ) {
                content()
            }
        }
    }
}

@Composable
fun PreferenceSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    // 左右 32dp 与分组卡片的内部文字对齐（卡片外边距 16dp + 行内边距 16dp）。
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = if (LocalPreferenceStyle.current == PreferenceStyle.Compact) 20.dp else 16.dp, bottom = 8.dp),
    )
}

/**
 * 分组标题下的小字备注：用更小的字号和次要文字色说明整组的前提或限制。
 * 左右缩进与 [PreferenceSectionTitle] 一致，紧跟标题摆放。
 *
 * @property color 文字颜色，默认次要文字色；需要强调失败原因时可传错误色。
 */
@Composable
fun PreferenceSectionNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 0.dp, bottom = 10.dp),
    )
}

/**
 * One UI 风格的设置分组卡片：大圆角、白（深色为略亮表面）底容器。
 * 内部按行摆放 [PreferenceListItem] 等内容；行间分隔线由行的 `showDivider` 控制，
 * 调用方保证组内除末行外均开启分隔线。
 */
@Composable
fun PreferenceGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(if (LocalPreferenceStyle.current == PreferenceStyle.Compact) 16.dp else 24.dp))
            .background(preferenceGroupCardColor()),
        content = content,
    )
}

/**
 * One UI 风格的设置行：左侧彩色圆形图标底 + 标题/副标题 + 可选尾部内容。
 *
 * 行本身无卡片背景，应放在 [PreferenceGroupCard] 内成组使用；
 * `showDivider` 为 true 时在行底部绘制与文字左缘对齐的内缩分隔线。
 *
 * @property iconContainer 图标圆形底色，默认主题蓝。
 * @property iconTint 圆底上的字形色，默认取 onPrimary 与 primary 圆底成对；禁用等特殊态可改为灰色组合。
 * @property iconSize 图标本身的尺寸；品牌图标可使用与容器相同的尺寸以保留原始外观。
 * @property subtitleMaxLines 说明文字的最大行数；需要完整解释功能的设置项可单独放宽。
 * @property titleColor 标题文字色，默认继承行内内容色；禁用态可只降级文字而不用整行透明度。
 * @property subtitleColor 说明文字颜色，默认次要文字色。
 * @property onLongClick 长按行为；提供后行改为 combinedClickable，用于进入多选等模式。
 * @property checked 与 [onCheckedChange] 同时提供时整行改为 toggleable（Role.Switch），
 * 涟漪覆盖整行；开关行请直接使用 [PreferenceSwitchRow]。
 * 此时行内尾部的开关组件应传空回调仅作展示，避免重复语义与局部涟漪。
 * @property rowEnabled 行级交互（点击或整行开关）的可用性；只影响交互，不改文字与图标外观。
 * @param leadingIcon 自定义图标槽位；提供时以透明背景替换默认图标，沿用列表的对齐尺寸。
 */
@Composable
fun PreferenceListItem(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    rowEnabled: Boolean = true,
    iconContainer: Color = if (LocalPreferenceStyle.current == PreferenceStyle.Compact) Color.Transparent else MaterialTheme.colorScheme.primary,
    iconTint: Color = if (LocalPreferenceStyle.current == PreferenceStyle.Compact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
    iconSize: Dp = GimiListDefaults.IconSize,
    showDivider: Boolean = false,
    subtitleMaxLines: Int = 2,
    titleColor: Color = Color.Unspecified,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    leadingIcon: (@Composable (Modifier) -> Unit)? = null,
) {
    val compact = LocalPreferenceStyle.current == PreferenceStyle.Compact
    val containerSize = if (compact) maxOf(GimiListDefaults.LeadingSize, iconSize) else 34.dp
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = GimiListDefaults.MinHeight)
                .then(
                    when {
                        checked != null && onCheckedChange != null -> Modifier.toggleable(
                            value = checked,
                            enabled = rowEnabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                        onClick == null -> Modifier
                        onLongClick == null -> Modifier.clickable(
                            role = Role.Button,
                            enabled = rowEnabled,
                            onClick = onClick,
                        )
                        else -> Modifier.combinedClickable(
                            role = Role.Button,
                            enabled = rowEnabled,
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    },
                )
                .padding(horizontal = GimiListDefaults.HorizontalPadding, vertical = GimiListDefaults.VerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(containerSize)
                    .background(color = if (leadingIcon == null) iconContainer else Color.Transparent, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (leadingIcon != null) {
                    leadingIcon(Modifier.size(24.dp))
                } else if (icon != null) Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = GimiListDefaults.ContentSpacing, end = if (trailingContent == null) 0.dp else 8.dp),
            ) {
                // 空标题不渲染，供只有图标与尾部内容的紧凑行（如 icon + switch）复用同一骨架。
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        maxLines = if (compact) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            trailingContent?.invoke(this)
            if (compact && trailingContent == null && onClick != null && onLongClick == null) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showDivider) {
            // 分隔线对齐文字左缘，品牌图标与普通图标共用槽位和间距。
            HorizontalDivider(
                modifier = Modifier.padding(start = GimiListDefaults.HorizontalPadding + containerSize + GimiListDefaults.ContentSpacing, end = GimiListDefaults.HorizontalPadding),
                color = if (compact) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * 统一的开关行：整行承担开关交互（Role.Switch，涟漪覆盖整行），尾部开关只作状态展示。
 * 对齐聊天抽屉与设置页的开关行交互，覆盖 UI 设计的三种布局：
 * 1. icon + menu + switch：传 [icon]（或 [leadingIcon]）与 [title]/[description]；
 * 2. icon + switch：只传 [icon]（或 [leadingIcon]），不传标题与说明；
 * 3. menu + switch：只传 [title]/[description]，不传图标（也可只传开关，左侧留空）。
 *
 * @property icon 标准图标槽：Compact 样式为透明底 24dp 图标，常规样式为主题蓝圆形底。
 * @property leadingIcon 自定义图标槽（品牌图标、函数图标等）；提供时替代 [icon]。
 * @property title 主标题；空时不渲染。
 * @property description 说明文字；空时不渲染。
 * @property enabled 整行交互与开关视觉的可用性；禁用时文字与图标同步降为 38% 透明度。
 * @property showDivider 行底内缩分隔线，对齐文字左缘。
 */
@Composable
fun PreferenceSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    leadingIcon: (@Composable (Modifier) -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    showDivider: Boolean = false,
) {
    val compact = LocalPreferenceStyle.current == PreferenceStyle.Compact
    PreferenceListItem(
        icon = icon,
        title = title.orEmpty(),
        subtitle = description,
        modifier = modifier,
        iconTint = when {
            compact && !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            compact -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onPrimary
        },
        showDivider = showDivider,
        leadingIcon = leadingIcon,
        checked = checked,
        onCheckedChange = onCheckedChange,
        rowEnabled = enabled,
        titleColor = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        subtitleColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        trailingContent = {
            GimiSwitch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

/**
 * 带图标的状态横幅，替代裸色文字提示，使信息与错误在设置页中有一致的容器语言。
 */
enum class PreferenceBannerTone { Info, Error }

@Composable
fun PreferenceBanner(
    text: String,
    tone: PreferenceBannerTone = PreferenceBannerTone.Info,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (tone) {
        PreferenceBannerTone.Info -> MaterialTheme.colorScheme.secondaryContainer
        PreferenceBannerTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        PreferenceBannerTone.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        PreferenceBannerTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = when (tone) {
        PreferenceBannerTone.Info -> Icons.Default.Info
        PreferenceBannerTone.Error -> Icons.Default.Warning
    }
    // 外边距与分组卡片对齐，横幅在画布上与卡片同宽。
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferencePageContainerPreview() {
    GimiTheme {
        PreferencePageContainer {
            PreferenceSectionTitle(text = "分组标题")
            PreferenceGroupCard {
                PreferenceListItem(
                    icon = Icons.Default.Info,
                    title = "设置项",
                    subtitle = "设置项副标题",
                    onClick = {},
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceSectionTitlePreview() {
    GimiTheme {
        PreferenceSectionTitle(text = "分组标题")
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceGroupCardPreview() {
    GimiTheme {
        PreferenceGroupCard {
            PreferenceListItem(
                icon = Icons.Default.Info,
                title = "设置项一",
                onClick = {},
                showDivider = true,
            )
            PreferenceListItem(
                icon = Icons.Default.Warning,
                title = "设置项二",
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceListItemPreview() {
    GimiTheme {
        PreferenceListItem(
            icon = Icons.Default.Info,
            title = "设置项",
            subtitle = "设置项副标题，最多两行展示",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceListItemWithTrailingPreview() {
    GimiTheme {
        PreferenceListItem(
            icon = Icons.Default.Info,
            title = "带尾部内容的设置项",
            showDivider = false,
            trailingContent = {
                Text(
                    text = "尾部",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceSwitchRowPreview() {
    GimiTheme {
        PreferenceGroupCard {
            PreferenceSwitchRow(
                icon = Icons.Default.Info,
                title = "图标 + 标题/说明 + 开关",
                description = "说明文字最多两行展示",
                checked = true,
                onCheckedChange = {},
                showDivider = true,
            )
            PreferenceSwitchRow(
                icon = Icons.Default.Info,
                title = "关闭的开关行",
                description = "说明文字",
                checked = false,
                onCheckedChange = {},
                showDivider = true,
            )
            PreferenceSwitchRow(
                icon = Icons.Default.Info,
                title = "禁用的开关行",
                description = "整行交互与视觉均不可用",
                checked = false,
                enabled = false,
                onCheckedChange = {},
                showDivider = true,
            )
            PreferenceSwitchRow(
                icon = Icons.Default.Info,
                title = "图标 + 开关",
                checked = true,
                onCheckedChange = {},
                showDivider = true,
            )
            PreferenceSwitchRow(
                title = "标题 + 开关",
                checked = false,
                onCheckedChange = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceBannerInfoPreview() {
    GimiTheme {
        PreferenceBanner(
            text = "信息提示文案",
            tone = PreferenceBannerTone.Info,
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceBannerErrorPreview() {
    GimiTheme {
        PreferenceBanner(
            text = "错误提示文案",
            tone = PreferenceBannerTone.Error,
        )
    }
}
