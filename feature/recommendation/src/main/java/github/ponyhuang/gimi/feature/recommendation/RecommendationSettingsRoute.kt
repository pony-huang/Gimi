package github.ponyhuang.gimi.feature.recommendation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

@Composable
fun RecommendationSettingsRoute(
    onBack: () -> Unit,
    onOpenToolAuthorization: () -> Unit,
    onOpenFastModelSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.recommendation_settings_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        RecommendationSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            onOpenToolAuthorization = onOpenToolAuthorization,
            onOpenFastModelSettings = onOpenFastModelSettings,
            modifier = scaffoldModifier,
        )
    }
}
