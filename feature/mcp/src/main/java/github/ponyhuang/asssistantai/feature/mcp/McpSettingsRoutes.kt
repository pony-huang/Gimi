package github.ponyhuang.asssistantai.feature.mcp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun McpServerListRoute(
    onNavigateToEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    McpServerListScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateToEditor = onNavigateToEditor,
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
    CloseEffect(state.shouldClose, onBack, viewModel)
    McpServerImportScreen(
        state = state,
        onAction = viewModel::onAction,
        onCancel = onBack,
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
    CloseEffect(state.shouldClose, onBack, viewModel)
    McpServerEditorScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
private fun CloseEffect(
    shouldClose: Boolean,
    onBack: () -> Unit,
    viewModel: McpSettingsViewModel,
) {
    LaunchedEffect(shouldClose) {
        if (shouldClose) {
            onBack()
            viewModel.onAction(McpSettingsAction.CloseConsumed)
        }
    }
}
