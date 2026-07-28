package github.ponyhuang.asssistantai.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    appVersionName: String,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToVoiceWake: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToModelService -> onNavigateToModelService()
                SettingsEffect.NavigateToDefaultModels -> onNavigateToDefaultModels()
                SettingsEffect.NavigateToVoiceWake -> onNavigateToVoiceWake()
                SettingsEffect.NavigateToMcpServers -> onNavigateToMcpServers()
                SettingsEffect.NavigateToSkills -> onNavigateToSkills()
                SettingsEffect.NavigateToWorkFiles -> onNavigateToWorkFiles()
                SettingsEffect.NavigateToPermissions -> onNavigateToPermissions()
                SettingsEffect.NavigateToToolAuthorization -> onNavigateToToolAuthorization()
            }
        }
    }

    SettingsScreen(
        state = state,
        appVersionName = appVersionName,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
