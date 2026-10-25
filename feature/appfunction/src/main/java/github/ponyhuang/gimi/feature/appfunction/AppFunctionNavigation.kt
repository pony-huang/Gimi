package github.ponyhuang.gimi.feature.appfunction

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** AppFunction 设置目的地，跨能力跳转由 app 层组合。 */
sealed interface AppFunctionDestination : NavKey {
    /** 可发现的应用列表。 */
    @Serializable data object Apps : AppFunctionDestination
    /** 指定包名的应用说明及功能详情。 */
    @Serializable data class Info(val packageName: String) : AppFunctionDestination
}

/** 解析 AppFunction 自有页面。 */
@Composable
fun AppFunctionEntryProvider(destination: NavKey, onBack: () -> Unit, navigate: (NavKey) -> Unit): Boolean = when (destination) {
    AppFunctionDestination.Apps -> {
        AppFunctionRoute(null, onBack, { navigate(AppFunctionDestination.Info(it)) })
        true
    }
    is AppFunctionDestination.Info -> {
        AppFunctionRoute(destination.packageName, onBack, {})
        true
    }
    else -> false
}

@Composable
private fun AppFunctionRoute(packageName: String?, onBack: () -> Unit, onViewInfo: (String) -> Unit,
    viewModel: AppFunctionSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(AppFunctionAction.Refresh) }
    val context = LocalContext.current
    val packages = state.catalog.apps.map { it.packageName }
    val icons by produceState<Map<String, ImageBitmap>>(emptyMap(), packages, context) {
        value = withContext(Dispatchers.IO) {
            packages.mapNotNull { name ->
                try {
                    val drawable = context.packageManager.getApplicationIcon(name)
                    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, 96, 96)
                    drawable.draw(Canvas(bitmap))
                    name to bitmap.asImageBitmap()
                } catch (_: PackageManager.NameNotFoundException) { null }
                catch (_: SecurityException) { null }
            }.toMap()
        }
    }
    PreferenceScaffold(title = stringResource(if (packageName == null) R.string.appfunction_title else R.string.appfunction_info_title),
        style = PreferenceStyle.Compact, onBack = onBack) { modifier ->
        AppFunctionScreen(state, packageName, icons, viewModel::onAction, onViewInfo, modifier)
    }
}
