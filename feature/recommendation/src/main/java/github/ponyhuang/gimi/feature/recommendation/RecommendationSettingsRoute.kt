package github.ponyhuang.gimi.feature.recommendation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecommendationSettingsRoute(
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecommendationSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenPermissions = onOpenPermissions,
        modifier = modifier,
    )
}

