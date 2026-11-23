package github.ponyhuang.gimi.feature.recommendation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 主题模式分段按钮的选项，包含模式、无障碍名称和显示图标。 */
private data class ThemeModeOption(
    val mode: ThemeMode,
    val contentDescriptionRes: Int,
    val icon: ImageVector,
)

/**
 * 主题分组内的主题模式三段组合按钮。
 *
 * 选中段按 Gimi 品牌规范渲染：primaryContainer 容器 + onPrimaryContainer 内容 + primary 描边，
 * 与设置页其他选中态保持同一套语义色。
 */
@Composable
internal fun ThemeModeSegmentedGroup(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ThemeModeOption(ThemeMode.SYSTEM, R.string.recommendation_theme_system, Icons.Default.BrightnessAuto),
        ThemeModeOption(ThemeMode.LIGHT, R.string.recommendation_theme_light, Icons.Default.LightMode),
        ThemeModeOption(ThemeMode.DARK, R.string.recommendation_theme_dark, Icons.Default.DarkMode),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.recommendation_theme_mode_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val selected = themeMode == option.mode
                SegmentedButton(
                    selected = selected,
                    onClick = { onThemeModeChange(option.mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    // 将勾选标记和模式图标一起居中，避免独立 icon 槽超出选中段的内嵌胶囊。
                    icon = {},
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            }
                            Icon(
                                imageVector = option.icon,
                                contentDescription = stringResource(option.contentDescriptionRes),
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        }
    }
}


@Preview(name = "主题 · 跟随系统", showBackground = true)
@Composable
private fun ThemeModeSystemPreview() {
    GimiTheme(darkTheme = false) {
        PreferenceGroupCard {
            ThemeModeSegmentedGroup(themeMode = ThemeMode.SYSTEM, onThemeModeChange = {})
        }
    }
}

@Preview(name = "主题 · 夜间", showBackground = true)
@Composable
private fun ThemeModeDarkPreview() {
    GimiTheme(darkTheme = true) {
        PreferenceGroupCard {
            ThemeModeSegmentedGroup(themeMode = ThemeMode.DARK, onThemeModeChange = {})
        }
    }
}
