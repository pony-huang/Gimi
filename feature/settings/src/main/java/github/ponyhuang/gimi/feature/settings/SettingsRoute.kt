package github.ponyhuang.gimi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    onNavigateToLocalModels: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    onNavigateToMobileUse: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 收集器随 ViewModel 保持稳定，但每次导航都使用最新的路由回调。
    val handleEffect by rememberUpdatedState<(SettingsEffect) -> Unit> { effect ->
        when (effect) {
            SettingsEffect.NavigateToLocalModels -> onNavigateToLocalModels()
            SettingsEffect.NavigateToModelService -> onNavigateToModelService()
            SettingsEffect.NavigateToDefaultModels -> onNavigateToDefaultModels()
            SettingsEffect.NavigateToMcpServers -> onNavigateToMcpServers()
            SettingsEffect.NavigateToPlugins -> onNavigateToPlugins()
            SettingsEffect.NavigateToSkills -> onNavigateToSkills()
            SettingsEffect.NavigateToWorkFiles -> onNavigateToWorkFiles()
            SettingsEffect.NavigateToWorkspace -> onNavigateToWorkspace()
            SettingsEffect.NavigateToPermissions -> onNavigateToPermissions()
            SettingsEffect.NavigateToToolAuthorization -> onNavigateToToolAuthorization()
            SettingsEffect.NavigateToMobileUse -> onNavigateToMobileUse()
            SettingsEffect.NavigateToRecommendations -> onNavigateToRecommendations()
            SettingsEffect.NavigateToMemory -> onNavigateToMemory()
            SettingsEffect.NavigateToAbout -> onNavigateToAbout()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { handleEffect(it) }
    }

    PreferenceScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) { scaffoldModifier ->
        SettingsScreen(
            state = state,
            appVersionName = appVersionName,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}
