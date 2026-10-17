package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 服务详情中控制官方工具是否进入会话抽屉的设置分区。 */
@Composable
fun OfficialToolsSection(
    serviceId: String,
    enabled: Boolean,
    serviceEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PreferenceSectionTitle(text = stringResource(R.string.modelsettings_section_official_tools))
        PreferenceGroupCard {
            PreferenceListItem(
                icon = Icons.Outlined.Handyman,
                iconSize = 32.dp,
                title = stringResource(R.string.modelsettings_official_tools_enabled),
                subtitle = officialToolsSummary(serviceId),
                subtitleMaxLines = Int.MAX_VALUE,
                trailingContent = {
                    Switch(
                        checked = enabled,
                        enabled = serviceEnabled,
                        onCheckedChange = onEnabledChange,
                    )
                },
            )
        }
    }
}

/** 厂商专属说明只影响展示；未配置的厂商沿用统一说明，后续在此补充资源映射。 */
@Composable
private fun officialToolsSummary(serviceId: String): String = stringResource(
    when (serviceId) {
        "deepseek" -> R.string.modelsettings_official_tools_deepseek_summary
        else -> R.string.modelsettings_official_tools_enabled_summary
    },
)

@Preview(name = "DeepSeek · 浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(name = "DeepSeek · 深色", showBackground = true, widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "DeepSeek · English", showBackground = true, widthDp = 390, locale = "en")
@Composable
private fun DeepSeekOfficialToolsSectionPreview() {
    AsssistantaiTheme {
        PreferencePageContainer(style = PreferenceStyle.Compact) {
            OfficialToolsSection(serviceId = "deepseek", enabled = true, serviceEnabled = true, onEnabledChange = {})
        }
    }
}

@Preview(name = "默认厂商 · 浅色", showBackground = true, widthDp = 390, locale = "zh")
@Preview(name = "默认厂商 · 深色", showBackground = true, widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DefaultOfficialToolsSectionPreview() {
    AsssistantaiTheme {
        PreferencePageContainer(style = PreferenceStyle.Compact) {
            OfficialToolsSection(serviceId = "glm", enabled = false, serviceEnabled = false, onEnabledChange = {})
        }
    }
}
