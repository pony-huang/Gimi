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
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 工具加载分段按钮的选项：模式与文案一一对应，顺序固定 全量加载|按需加载。 */
private data class ToolLoadingOption(
    val mode: ToolAccessMode,
    val labelRes: Int,
)

/**
 * 工具分组内的加载方式两段组合按钮。
 *
 * 全量/按需是互斥模式而非功能开关，两个选项并列展示比开关的「关=另一个模式」更直观；
 * 选中段按 Gimi 品牌规范渲染：primaryContainer 容器 + onPrimaryContainer 内容 + primary 描边，
 * 与主题模式分段按钮保持同一套语义色。
 */
@Composable
internal fun ToolLoadingSegmentedGroup(
    toolAccessMode: ToolAccessMode,
    onToolAccessModeChange: (ToolAccessMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ToolLoadingOption(ToolAccessMode.ALWAYS_AVAILABLE, R.string.recommendation_tool_loading_always),
        ToolLoadingOption(ToolAccessMode.ON_DEMAND, R.string.recommendation_tool_loading_on_demand),
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
                imageVector = Icons.Default.Rule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.recommendation_tool_loading_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = toolAccessMode == option.mode,
                    onClick = { onToolAccessModeChange(option.mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    label = { Text(stringResource(option.labelRes), maxLines = 1) },
                )
            }
        }
    }
}

@Preview(name = "工具 · 全量加载", showBackground = true)
@Composable
private fun ToolLoadingAlwaysPreview() {
    GimiTheme(darkTheme = false) {
        PreferenceGroupCard {
            ToolLoadingSegmentedGroup(ToolAccessMode.ALWAYS_AVAILABLE, onToolAccessModeChange = {})
        }
    }
}

@Preview(name = "工具 · 按需加载", showBackground = true)
@Composable
private fun ToolLoadingOnDemandPreview() {
    GimiTheme(darkTheme = false) {
        PreferenceGroupCard {
            ToolLoadingSegmentedGroup(ToolAccessMode.ON_DEMAND, onToolAccessModeChange = {})
        }
    }
}

@Preview(name = "工具 · 按需加载（夜间）", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ToolLoadingOnDemandDarkPreview() {
    GimiTheme(darkTheme = true) {
        PreferenceGroupCard {
            ToolLoadingSegmentedGroup(ToolAccessMode.ON_DEMAND, onToolAccessModeChange = {})
        }
    }
}
