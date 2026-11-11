package github.ponyhuang.gimi.feature.recommendation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

/** 「推荐更新」页：承载智能推荐开关、更新间隔与手动更新。 */
@Composable
fun RecommendationUpdateSettingsRoute(
    onBack: () -> Unit,
    onOpenFastModelSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.recommendation_update_page_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        RecommendationUpdateSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            onOpenFastModelSettings = onOpenFastModelSettings,
            modifier = scaffoldModifier,
        )
    }
}
