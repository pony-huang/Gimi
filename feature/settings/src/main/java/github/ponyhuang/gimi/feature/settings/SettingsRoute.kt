package github.ponyhuang.gimi.feature.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.settings.update.UpdateAction
import github.ponyhuang.gimi.feature.settings.update.UpdateDialog
import github.ponyhuang.gimi.feature.settings.update.UpdateEffect
import github.ponyhuang.gimi.feature.settings.update.UpdateViewModel
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

private const val PROJECT_URL = "https://github.com/pony-huang/Gimi"

/** APK 安装包 MIME 类型，调起系统安装器时使用。 */
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

@Composable
fun SettingsRoute(
    appVersionName: String,
    onBack: () -> Unit,
    onNavigateToModelService: () -> Unit,
    onNavigateToDefaultModels: () -> Unit,
    onNavigateToVoiceWake: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToWorkFiles: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToToolAuthorization: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToMemory: () -> Unit,
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
                SettingsEffect.NavigateToRecommendations -> onNavigateToRecommendations()
                SettingsEffect.NavigateToMemory -> onNavigateToMemory()
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
                UpdateEffect.OpenAppDetails -> context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri(),
                    ),
                )

                UpdateEffect.OpenUnknownSourceSettings -> context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".toUri(),
                    ),
                )

                is UpdateEffect.InstallApk -> context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(effect.apkContentUri.toUri(), APK_MIME_TYPE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )

                is UpdateEffect.ShowToast ->
                    Toast.makeText(context, effect.messageRes, Toast.LENGTH_SHORT).show()
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
            updateState = updateState,
            onUpdateAction = updateViewModel::onAction,
        )

        UpdateDialog(
            state = updateState,
            onAction = updateViewModel::onAction,
        )
    }
}