package github.ponyhuang.gimi.feature.sandbox

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import kotlinx.serialization.Serializable

/** 沙盒功能拥有的导航目的地。 */
sealed interface SandboxDestination : NavKey {
    /** E2B 开关及连接配置页。 */
    @Serializable data object Settings : SandboxDestination
}

/** app 负责跨功能导航；此入口只识别自身目的地。 */
@Composable
fun SandboxEntryProvider(destination: NavKey, onBack: () -> Unit): Boolean = when (destination) {
    SandboxDestination.Settings -> { SandboxSettingsRoute(onBack); true }
    else -> false
}

@Composable
private fun SandboxSettingsRoute(onBack: () -> Unit, viewModel: SandboxSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) { SandboxSettingsEffect.SAVED -> R.string.sandbox_saved; SandboxSettingsEffect.SAVE_FAILED -> R.string.sandbox_save_failed }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.sandbox_screen_title),
        onBack = onBack,
        bottomBar = { SandboxSettingsSaveBar(state, viewModel::onAction) },
    ) { modifier ->
        SandboxSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            onOpenDocumentation = { uriHandler.openUri("https://github.com/e2b-dev/E2B") },
            modifier = modifier,
        )
    }
}
