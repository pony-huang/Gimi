package github.ponyhuang.asssistantai.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.asssistantai.feature.settings.R
import github.ponyhuang.asssistantai.ui.navigation.SettingsScaffold
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val chatDisplayPreferences: ChatDisplayRepository,
) : ViewModel() {
    val showToolActivity = chatDisplayPreferences.showToolActivity

    fun setShowToolActivity(show: Boolean) = chatDisplayPreferences.setShowToolActivity(show)
}

@Composable
fun SettingsScreen(
    appVersionName: String,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToVoiceWake: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val showToolActivity by viewModel.showToolActivity.collectAsStateWithLifecycle()

    SettingsHomeContent(
        showToolActivity = showToolActivity,
        appVersionName = appVersionName,
        onNavigateToModelService = onNavigateToModelService,
        onNavigateToDefaultModels = onNavigateToDefaultModels,
        onNavigateToVoiceWake = onNavigateToVoiceWake,
        onNavigateToMcpServers = onNavigateToMcpServers,
        onNavigateToWorkFiles = onNavigateToWorkFiles,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateToToolAuthorization = onNavigateToToolAuthorization,
        onToggleToolActivity = viewModel::setShowToolActivity,
        modifier = modifier,
    )
}

@Composable
private fun SettingsHomeContent(
    showToolActivity: Boolean,
    appVersionName: String,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToVoiceWake: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    onToggleToolActivity: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.settings_model_service_title),
                    subtitle = stringResource(R.string.settings_model_service_subtitle),
                    onClick = onNavigateToModelService,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = stringResource(R.string.settings_default_model_title),
                    subtitle = stringResource(R.string.settings_default_model_subtitle),
                    onClick = onNavigateToDefaultModels,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.BluetoothAudio,
                    title = stringResource(R.string.settings_voice_wake_title),
                    subtitle = stringResource(R.string.settings_voice_wake_subtitle),
                    onClick = onNavigateToVoiceWake,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.settings_mcp_title),
                    subtitle = stringResource(R.string.settings_mcp_subtitle),
                    onClick = onNavigateToMcpServers,
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Default.Visibility,
                    title = stringResource(R.string.settings_chat_display_title),
                    subtitle = stringResource(R.string.settings_chat_display_subtitle),
                    onClick = { onToggleToolActivity(!showToolActivity) },
                    trailingContent = {
                        Switch(
                            checked = showToolActivity,
                            onCheckedChange = onToggleToolActivity,
                        )
                    },
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.settings_work_files_title),
                    subtitle = stringResource(R.string.settings_work_files_subtitle),
                    onClick = onNavigateToWorkFiles,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.settings_tool_authorization_title),
                    subtitle = stringResource(R.string.settings_tool_authorization_subtitle),
                    onClick = onNavigateToToolAuthorization,
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_permissions_title),
                    subtitle = stringResource(R.string.settings_permissions_subtitle),
                    onClick = onNavigateToPermissions,
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_subtitle, appVersionName),
                )
            }
        }
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
private annotation class SettingsFormFactorPreviews

@SettingsFormFactorPreviews
@Composable
private fun SettingsHomePreview() {
    AsssistantaiTheme {
        SettingsScaffold(title = stringResource(R.string.settings_title), onBack = {}) { modifier ->
            SettingsHomeContent(
                showToolActivity = true,
                appVersionName = "1.0",
                onNavigateToModelService = {},
                onNavigateToDefaultModels = {},
                onNavigateToVoiceWake = {},
                onNavigateToMcpServers = {},
                onNavigateToWorkFiles = {},
                onNavigateToPermissions = {},
                onNavigateToToolAuthorization = {},
                onToggleToolActivity = {},
                modifier = modifier,
            )
        }
    }
}
