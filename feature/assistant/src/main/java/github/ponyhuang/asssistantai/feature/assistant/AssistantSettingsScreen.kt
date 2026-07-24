package github.ponyhuang.asssistantai.feature.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.ui.settings.SettingsListItem
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer

/**
 * 无状态助理设置页。权限/磁贴的系统交互由 Route 回调承载。
 * 与其余设置页共用透明分组列表语言：状态在副标题，可操作项在尾部按钮，
 * 已就绪状态以尾部对勾图标给出肯定信号。
 */
@Composable
fun AssistantSettingsScreen(
    state: AssistantSettingsUiState,
    onRequestMicrophone: () -> Unit,
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            SettingsListItem(
                icon = Icons.Default.Mic,
                title = stringResource(R.string.assistant_settings_mic_section),
                subtitle = stringResource(
                    if (state.microphoneGranted) {
                        R.string.assistant_mic_granted
                    } else {
                        R.string.assistant_mic_missing
                    },
                ),
                onClick = if (!state.microphoneGranted) onRequestMicrophone else null,
                modifier = Modifier.testTag("assistant_mic_status"),
                trailingContent = {
                    if (!state.microphoneGranted) {
                        TextButton(
                            onClick = onRequestMicrophone,
                            modifier = Modifier.testTag("assistant_request_mic"),
                        ) {
                            Text(stringResource(R.string.assistant_mic_request))
                        }
                    } else {
                        ReadyIndicator()
                    }
                },
            )

            SettingsListItem(
                icon = Icons.Default.AddToHomeScreen,
                title = stringResource(R.string.assistant_settings_tile_section),
                subtitle = stringResource(R.string.assistant_tile_hint),
                onClick = onAddTile,
                trailingContent = {
                    TextButton(
                        onClick = onAddTile,
                        modifier = Modifier.testTag("assistant_add_tile"),
                    ) {
                        Text(stringResource(R.string.assistant_tile_add))
                    }
                },
            )
            if (state.tileAddRequested) {
                Caption(
                    text = stringResource(R.string.assistant_tile_added),
                    modifier = Modifier.testTag("assistant_tile_added"),
                )
            }
        }
    }
}

/** 行内说明文字：与列表项左对齐，承载辅助解释与操作反馈。 */
@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 24.dp),
    )
}

/** 已就绪的肯定信号，替代「无操作」的空白尾部。 */
@Composable
private fun ReadyIndicator() {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}
