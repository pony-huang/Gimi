package github.ponyhuang.gimi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

@Composable
fun SettingsRoute(
    appVersionName: String,
    onBack: () -> Unit,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToRemoteAgents: () -> Unit,
    onNavigateToAppFunctions: () -> Unit,
    onNavigateToSandbox: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToMobileUse: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // 收集器随 ViewModel 保持稳定，但每次导航都使用最新的路由回调。
    val handleEffect by rememberUpdatedState<(SettingsEffect) -> Unit> { effect ->
        when (effect) {
            SettingsEffect.NavigateToModelService -> onNavigateToModelService()
            SettingsEffect.NavigateToDefaultModels -> onNavigateToDefaultModels()
            SettingsEffect.NavigateToMcpServers -> onNavigateToMcpServers()
            SettingsEffect.NavigateToRemoteAgents -> onNavigateToRemoteAgents()
            SettingsEffect.NavigateToAppFunctions -> onNavigateToAppFunctions()
            SettingsEffect.NavigateToSandbox -> onNavigateToSandbox()
            SettingsEffect.NavigateToPlugins -> onNavigateToPlugins()
            SettingsEffect.NavigateToSkills -> onNavigateToSkills()
            SettingsEffect.NavigateToWorkspace -> onNavigateToWorkspace()
            SettingsEffect.NavigateToPermissions -> onNavigateToPermissions()
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

        style = PreferenceStyle.Compact,
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) { scaffoldModifier ->
        SettingsScreen(
            appVersionName = appVersionName,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}
