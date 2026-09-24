package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

/** 服务详情中控制官方工具是否进入会话抽屉的设置分区。 */
@Composable
fun OfficialToolsSection(
    enabled: Boolean,
    serviceEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PreferenceSectionTitle(text = stringResource(R.string.modelsettings_section_official_tools))
        PreferenceGroupCard {
            PreferenceListItem(
                icon = Icons.Outlined.Extension,
                title = stringResource(R.string.modelsettings_official_tools_enabled),
                subtitle = stringResource(R.string.modelsettings_official_tools_enabled_summary),
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
