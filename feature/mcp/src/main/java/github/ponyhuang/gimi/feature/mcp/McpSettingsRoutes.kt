package github.ponyhuang.gimi.feature.mcp

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun McpServerListRoute(
    onBack: () -> Unit,
    onAddServer: () -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    onCreateServer: () -> Unit,
    onImportServers: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceScaffold(
        title = stringResource(R.string.mcp_list_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onAddServer) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.mcp_add_server),
                )
            }
        },
    ) { scaffoldModifier ->
        McpServerListScreen(
            state = state,
            onAction = viewModel::onAction,
            onNavigateToEditor = onNavigateToEditor,
            onCreateServer = onCreateServer,
            onImportServers = onImportServers,
            modifier = scaffoldModifier,
        )
    }
}

@Composable
fun McpServerAddOptionsRoute(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceScaffold(
        title = stringResource(R.string.mcp_add_options_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        McpServerAddOptionsScreen(
            onCreate = onCreate,
            onImport = onImport,
            modifier = scaffoldModifier,
        )
    }
}

@Composable
fun McpServerImportRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CloseEffect(viewModel, onBack)
    PreferenceScaffold(
        title = stringResource(R.string.mcp_import_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        McpServerImportScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
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
    PreferenceScaffold(
        title = stringResource(
            if (serverId == null) R.string.mcp_add_server
            else R.string.mcp_edit_server,
        ),
        onBack = onBack,
    ) { scaffoldModifier ->
        McpServerEditorScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}

@Composable
private fun CloseEffect(
    viewModel: McpSettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                McpSettingsEffect.Close -> onBack()
                McpSettingsEffect.Saved -> {
                    Toast.makeText(context, R.string.mcp_save_success, Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        }
    }
}