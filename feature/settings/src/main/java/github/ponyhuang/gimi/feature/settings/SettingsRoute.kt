package github.ponyhuang.gimi.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import github.ponyhuang.gimi.domain.workspace.WorkspaceDocumentsContract
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
    onNavigateToMobileUse: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // 收集器随 ViewModel 保持稳定，但每次导航都使用最新的路由回调。
    val handleEffect by rememberUpdatedState<(SettingsEffect) -> Unit> { effect ->
        when (effect) {
            SettingsEffect.NavigateToModelService -> onNavigateToModelService()
            SettingsEffect.NavigateToDefaultModels -> onNavigateToDefaultModels()
            SettingsEffect.NavigateToMcpServers -> onNavigateToMcpServers()
            SettingsEffect.NavigateToRemoteAgents -> onNavigateToRemoteAgents()
            SettingsEffect.NavigateToAppFunctions -> onNavigateToAppFunctions()
            SettingsEffect.NavigateToSandbox -> onNavigateToSandbox()
            SettingsEffect.NavigateToMobileUse -> onNavigateToMobileUse()
            SettingsEffect.NavigateToPlugins -> onNavigateToPlugins()
            SettingsEffect.NavigateToSkills -> onNavigateToSkills()
            SettingsEffect.NavigateToWorkspace -> openWorkspaceDocuments(context)
            SettingsEffect.NavigateToPermissions -> onNavigateToPermissions()
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

private fun openWorkspaceDocuments(context: Context) {
    val root = DocumentsContract.buildRootUri(
        WorkspaceDocumentsContract.authority(context.packageName),
        WorkspaceDocumentsContract.ROOT_ID,
    )
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.settings_workspace_files_unavailable, Toast.LENGTH_LONG).show()
    }
}
