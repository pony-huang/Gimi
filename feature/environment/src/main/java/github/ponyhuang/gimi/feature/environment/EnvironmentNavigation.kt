package github.ponyhuang.gimi.feature.environment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import kotlinx.serialization.Serializable
import androidx.core.net.toUri

/** 运行环境自身的目的地，跨功能导航由 app 组合。 */
sealed interface EnvironmentDestination : NavKey {
    /** 环境独立开关与配置入口。 */
    @Serializable data object Overview : EnvironmentDestination
    /** 基础环境安装状态。 */
    @Serializable data object Local : EnvironmentDestination
    /** 本地持久变量配置。 */
    @Serializable data object Variables : EnvironmentDestination
}

@Composable
fun EnvironmentEntryProvider(destination: NavKey, onBack: () -> Unit, navigate: (NavKey) -> Unit,
    onOpenE2b: () -> Unit, onInstallPackage: (String) -> Unit): Boolean {
    if (destination !is EnvironmentDestination) return false
    val viewModel: EnvironmentViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val recovery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.refreshStatus()
        if (result.resultCode != Activity.RESULT_OK) Toast.makeText(context, R.string.environment_recovery_failed, Toast.LENGTH_LONG).show()
    }
    var pendingInstall by rememberSaveable { mutableStateOf<String?>(null) }
    val installPackage by rememberUpdatedState(onInstallPackage)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
        // 从未知来源授权页返回后继续安装；取消授权时清空待办，避免以后意外弹出安装页。
        val path = pendingInstall
        pendingInstall = null
        if (path != null && context.packageManager.canRequestPackageInstalls()) installPackage(path)
    }
    LaunchedEffect(viewModel) {
        viewModel.packageInstalls.collect { path ->
            pendingInstall = if (context.packageManager.canRequestPackageInstalls()) null else path
            installPackage(path)
        }
    }
    LaunchedEffect(destination) {
        when (destination) {
            // 仅在从未加载过时拉取，避免离开再进入覆盖未保存的编辑（包括已删除全部变量的空列表）；本地环境页也需要计数副标题。
            EnvironmentDestination.Local, EnvironmentDestination.Variables -> if (!state.variablesLoaded) viewModel.loadVariables()
            else -> Unit
        }
    }
    val actions = EnvironmentActions(viewModel::enableLocal, viewModel::requestClose, viewModel::closeLocal, viewModel::dismissClose,
        viewModel::install, viewModel::cancelInstall, viewModel::setE2b, viewModel::addVariable, viewModel::editVariable,
        viewModel::removeVariable, viewModel::toggleVariable, viewModel::saveVariables, viewModel::requestRuntimeUpdate,
        viewModel::updateRuntime, viewModel::dismissRuntimeUpdate)
    val title = when (destination) {
        EnvironmentDestination.Overview -> R.string.environment_title
        EnvironmentDestination.Local -> R.string.environment_local
        EnvironmentDestination.Variables -> R.string.environment_variables
    }
    PreferenceScaffold(title = stringResource(title), onBack = onBack, style = PreferenceStyle.Compact,
        bottomBar = { if (destination == EnvironmentDestination.Variables) EnvironmentVariablesSaveBar(state, actions.save) }) { modifier ->
        EnvironmentScreen(destination, state, actions, navigate, onOpenE2b,
            { uriHandler.openUri("https://hub.docker.com/_/debian/tags?name=trixie") }, {
                val root = DocumentsContract.buildRootUri("github.ponyhuang.gimi.environment.runtime.documents", "local")
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.environment_files_unavailable, Toast.LENGTH_LONG).show()
                }
            }, {
                try {
                    recovery.launch(Intent("github.ponyhuang.gimi.environment.RECOVER")
                        .setClassName("github.ponyhuang.gimi.environment.runtime", "github.ponyhuang.gimi.environment.runtime.RuntimeActivity"))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.environment_recovery_failed, Toast.LENGTH_LONG).show()
                }
            }, {
                try {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:github.ponyhuang.gimi.environment.runtime".toUri()))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.environment_files_unavailable, Toast.LENGTH_LONG).show()
                }
            }, modifier)
    }
    return true
}
