package github.ponyhuang.gimi.feature.mcp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun McpServerListRoute(
    onNavigateToEditor: (String?) -> Unit,
    onCreateServer: () -> Unit,
    onImportServers: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    McpServerListScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateToEditor = onNavigateToEditor,
        onCreateServer = onCreateServer,
        onImportServers = onImportServers,
        modifier = modifier,
    )
}

@Composable
fun McpServerImportRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CloseEffect(viewModel, onBack)
    McpServerImportScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
fun McpServerEditorRoute(
    serverId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(serverId) {
        viewModel.onAction(McpSettingsAction.LoadEditor(serverId))
    }
    CloseEffect(viewModel, onBack)
    McpServerEditorScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
private fun CloseEffect(
    viewModel: McpSettingsViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                McpSettingsEffect.Close -> onBack()
            }
        }
    }
}
