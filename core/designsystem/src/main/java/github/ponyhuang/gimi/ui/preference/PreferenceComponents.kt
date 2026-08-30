package github.ponyhuang.gimi.ui.preference

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
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

@Composable
private fun preferenceGroupCardColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) PreferenceGroupCardDark else PreferenceGroupCardLight
}

@Composable
fun PreferencePageContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(preferenceCanvasColor()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter),
        ) {
            content()
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
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * Transparent settings section retained as a compatibility wrapper for form-heavy pages.
 * It deliberately has no card shape, elevation, or alternate surface colour.
 * 过渡保留：待全部调用方迁移到 [PreferenceGroupCard] 后移除。
 */
@Composable
fun PreferenceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        content = content,
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
            .clip(RoundedCornerShape(24.dp))
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
 */
@Composable
fun PreferenceListItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    iconContainer: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = Color.White,
    showDivider: Boolean = false,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color = iconContainer, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = if (trailingContent == null) 0.dp else 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            trailingContent?.invoke(this)
        }
        if (showDivider) {
            // 内缩到文字左缘（行内边距 16 + 圆形 34 + 间距 16）。
            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun PreferenceNavigationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.primary,
    showDivider: Boolean = false,
) {
    PreferenceListItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        iconContainer = iconContainer,
        showDivider = showDivider,
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
