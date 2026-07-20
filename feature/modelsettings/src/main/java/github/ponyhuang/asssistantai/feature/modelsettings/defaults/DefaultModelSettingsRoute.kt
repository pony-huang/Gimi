package github.ponyhuang.asssistantai.feature.modelsettings.defaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DefaultModelSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: DefaultModelSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DefaultModelSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
