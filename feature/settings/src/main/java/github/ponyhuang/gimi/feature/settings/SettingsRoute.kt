package github.ponyhuang.gimi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun SettingsRoute(
    appVersionName: String,
    onBack: () -> Unit,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToModelService -> onNavigateToModelService()
                SettingsEffect.NavigateToDefaultModels -> onNavigateToDefaultModels()
                SettingsEffect.NavigateToMcpServers -> onNavigateToMcpServers()
                SettingsEffect.NavigateToPlugins -> onNavigateToPlugins()
                SettingsEffect.NavigateToSkills -> onNavigateToSkills()
                SettingsEffect.NavigateToWorkFiles -> onNavigateToWorkFiles()
                SettingsEffect.NavigateToWorkspace -> onNavigateToWorkspace()
                SettingsEffect.NavigateToPermissions -> onNavigateToPermissions()
                SettingsEffect.NavigateToToolAuthorization -> onNavigateToToolAuthorization()
                SettingsEffect.NavigateToRecommendations -> onNavigateToRecommendations()
                SettingsEffect.NavigateToMemory -> onNavigateToMemory()
                SettingsEffect.NavigateToAbout -> onNavigateToAbout()
            }
        }
    }

    PreferenceScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        SettingsScreen(
            state = state,
            appVersionName = appVersionName,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}
