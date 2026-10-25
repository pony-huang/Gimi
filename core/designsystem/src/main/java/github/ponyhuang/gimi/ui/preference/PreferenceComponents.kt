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
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
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
 */
@Composable
fun PreferenceSectionNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * @property iconTint 圆底上的字形色，默认白色；禁用等特殊态可改为灰色组合。
 * @property iconSize 图标本身的尺寸；品牌图标可使用与容器相同的尺寸以保留原始外观。
 * @property subtitleMaxLines 说明文字的最大行数；需要完整解释功能的设置项可单独放宽。
 * @property onLongClick 长按行为；提供后行改为 combinedClickable，用于进入多选等模式。
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
    iconContainer: Color = if (LocalPreferenceStyle.current == PreferenceStyle.Compact) Color.Transparent else MaterialTheme.colorScheme.primary,
    iconTint: Color = if (LocalPreferenceStyle.current == PreferenceStyle.Compact) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
    iconSize: Dp = GimiListDefaults.IconSize,
    showDivider: Boolean = false,
    subtitleMaxLines: Int = 2,
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
                        onClick == null -> Modifier
                        onLongClick == null -> Modifier.clickable(role = Role.Button, onClick = onClick)
                        else -> Modifier.combinedClickable(
                            role = Role.Button,
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
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * 带开关的设置行。开关值由调用方持有，整行点击可独立配置，避免与导航型行混淆。
 * [switchEnabled] 只控制开关；行点击的可用性由 [onClick] 决定。
 */
@Composable
fun PreferenceSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    switchEnabled: Boolean = true,
    iconContainer: Color = if (LocalPreferenceStyle.current == PreferenceStyle.Compact) Color.Transparent else MaterialTheme.colorScheme.primary,
    iconTint: Color = if (LocalPreferenceStyle.current == PreferenceStyle.Compact) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
    iconSize: Dp = GimiListDefaults.IconSize,
    showDivider: Boolean = false,
) {
    PreferenceListItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
        iconContainer = iconContainer,
        iconTint = iconTint,
        iconSize = iconSize,
        showDivider = showDivider,
        trailingContent = {
            GimiSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = switchEnabled,
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
    AsssistantaiTheme {
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
    AsssistantaiTheme {
        PreferenceSectionTitle(text = "分组标题")
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceGroupCardPreview() {
    AsssistantaiTheme {
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
    AsssistantaiTheme {
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
    AsssistantaiTheme {
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
private fun PreferenceSwitchItemPreview() {
    AsssistantaiTheme {
        PreferenceGroupCard {
            PreferenceSwitchItem(
                icon = Icons.Default.Info,
                title = "开关设置项",
                checked = true,
                onCheckedChange = {},
                showDivider = true,
            )
            PreferenceSwitchItem(
                icon = Icons.Default.Info,
                title = "禁用的开关设置项",
                checked = false,
                switchEnabled = false,
                onCheckedChange = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferenceBannerInfoPreview() {
    AsssistantaiTheme {
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
    AsssistantaiTheme {
        PreferenceBanner(
            text = "错误提示文案",
            tone = PreferenceBannerTone.Error,
        )
    }
}
