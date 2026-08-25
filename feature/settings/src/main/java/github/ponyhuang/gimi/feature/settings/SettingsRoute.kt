package github.ponyhuang.gimi.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.settings.update.UpdateAction
import github.ponyhuang.gimi.feature.settings.update.UpdateDialog
import github.ponyhuang.gimi.feature.settings.update.UpdateEffect
import github.ponyhuang.gimi.feature.settings.update.UpdateViewModel

private const val PROJECT_URL = "https://github.com/pony-huang/Gimi"

@Composable
fun SettingsRoute(
    appVersionName: String,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToVoiceWake: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToModelService -> onNavigateToModelService()
                SettingsEffect.NavigateToDefaultModels -> onNavigateToDefaultModels()
                SettingsEffect.NavigateToVoiceWake -> onNavigateToVoiceWake()
                SettingsEffect.NavigateToMcpServers -> onNavigateToMcpServers()
                SettingsEffect.NavigateToPlugins -> onNavigateToPlugins()
                SettingsEffect.NavigateToSkills -> onNavigateToSkills()
                SettingsEffect.NavigateToWorkFiles -> onNavigateToWorkFiles()
                SettingsEffect.NavigateToPermissions -> onNavigateToPermissions()
                SettingsEffect.NavigateToToolAuthorization -> onNavigateToToolAuthorization()
                SettingsEffect.OpenProjectPage -> context.startActivity(
                    Intent(Intent.ACTION_VIEW, PROJECT_URL.toUri()),
                )
            }
        }
    }

    LaunchedEffect(updateViewModel) {
        updateViewModel.onAction(UpdateAction.ScreenEntered)
        updateViewModel.effects.collect { effect ->
            when (effect) {
                is UpdateEffect.LaunchIntent -> context.startActivity(effect.intent)
                is UpdateEffect.ShowToast ->
                    Toast.makeText(context, effect.messageRes, Toast.LENGTH_SHORT).show()
            }
        }
    }

    SettingsScreen(
        state = state,
        appVersionName = appVersionName,
        onAction = viewModel::onAction,
        modifier = modifier,
        updateState = updateState,
        onUpdateAction = updateViewModel::onAction,
    )

    UpdateDialog(
        state = updateState,
        onAction = updateViewModel::onAction,
    )
}
