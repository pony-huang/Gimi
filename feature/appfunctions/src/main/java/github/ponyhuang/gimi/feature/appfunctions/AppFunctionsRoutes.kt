package github.ponyhuang.gimi.feature.appfunctions

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** AppFunctions 应用目录路由。 */
@Composable
fun AppFunctionsSettingsRoute(
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppFunctionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppFunctionsEffects(viewModel)
    AppFunctionsSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenApp = onOpenApp,
        modifier = modifier,
    )
}

/** 单个提供应用的函数配置路由。 */
@Composable
fun AppFunctionAppDetailRoute(
    packageName: String,
    modifier: Modifier = Modifier,
    viewModel: AppFunctionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppFunctionsEffects(viewModel)
    AppFunctionAppDetailScreen(
        packageName = packageName,
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
private fun AppFunctionsEffects(viewModel: AppFunctionsViewModel) {
    val context = LocalContext.current
    val busyMessage = stringResource(R.string.appfunctions_agent_busy)
    val unavailableMessage = stringResource(R.string.appfunctions_unavailable_action)
    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                AppFunctionsEffect.AgentBusy -> busyMessage
                AppFunctionsEffect.FeatureUnavailable -> unavailableMessage
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
