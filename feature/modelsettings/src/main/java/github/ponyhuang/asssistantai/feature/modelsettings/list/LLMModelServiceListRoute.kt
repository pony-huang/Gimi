package github.ponyhuang.asssistantai.feature.modelsettings.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ModelServiceListRoute(
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LLMModelServiceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LLMModelServiceListScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateToDetail = onNavigateToDetail,
        modifier = modifier,
    )
}
