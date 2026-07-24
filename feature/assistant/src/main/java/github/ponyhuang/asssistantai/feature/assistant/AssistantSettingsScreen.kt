package github.ponyhuang.asssistantai.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** 无状态助理设置页。权限/磁贴的系统交互由 Route 回调承载。 */
@Composable
fun AssistantSettingsScreen(
    state: AssistantSettingsUiState,
    onRequestMicrophone: () -> Unit,
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard(title = stringResource(R.string.assistant_settings_mic_section)) {
            Text(
                text = stringResource(
                    if (state.microphoneGranted) {
                        R.string.assistant_mic_granted
                    } else {
                        R.string.assistant_mic_missing
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("assistant_mic_status"),
            )
            if (!state.microphoneGranted) {
                Button(
                    onClick = onRequestMicrophone,
                    modifier = Modifier.testTag("assistant_request_mic"),
                ) {
                    Text(stringResource(R.string.assistant_mic_request))
                }
            }
        }

        SettingsCard(title = stringResource(R.string.assistant_settings_tile_section)) {
            Text(
                text = stringResource(R.string.assistant_tile_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onAddTile,
                modifier = Modifier.testTag("assistant_add_tile"),
            ) {
                Text(stringResource(R.string.assistant_tile_add))
            }
            if (state.tileAddRequested) {
                Text(
                    text = stringResource(R.string.assistant_tile_added),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("assistant_tile_added"),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}
