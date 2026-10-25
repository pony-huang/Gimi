package github.ponyhuang.gimi.feature.a2a

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
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

@Composable
fun RemoteAgentListRoute(
    onBack: () -> Unit,
    onAddAgent: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: A2aSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.a2a_list_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onAddAgent) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.a2a_add_agent),
                )
            }
        },
    ) { scaffoldModifier ->
        RemoteAgentListScreen(
            state = state,
            onAction = viewModel::onAction,
            onCreateAgent = onAddAgent,
            onNavigateToEditor = onNavigateToEditor,
            modifier = scaffoldModifier,
        )
    }
}

@Composable
fun RemoteAgentEditorRoute(
    agentId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: A2aSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(agentId) {
        viewModel.onAction(A2aSettingsAction.LoadEditor(agentId))
    }
    CloseEffect(viewModel, onBack)
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(
            if (agentId == null) R.string.a2a_add_agent
            else R.string.a2a_edit_agent,
        ),
        onBack = onBack,
        bottomBar = { RemoteAgentEditorSaveBar(state, viewModel::onAction) },
    ) { scaffoldModifier ->
        RemoteAgentEditorScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}

@Composable
private fun CloseEffect(
    viewModel: A2aSettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                A2aSettingsEffect.Close -> onBack()
                A2aSettingsEffect.Saved -> {
                    Toast.makeText(context, R.string.a2a_save_success, Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        }
    }
}
