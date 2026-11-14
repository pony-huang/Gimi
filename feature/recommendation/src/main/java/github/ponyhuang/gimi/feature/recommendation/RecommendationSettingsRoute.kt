package github.ponyhuang.gimi.feature.recommendation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

/** 「个性化选项」页：承载提示词、对话界面与工具加载等偏好开关；设备控制已迁至设置一级页。 */
@Composable
fun RecommendationSettingsRoute(
    onBack: () -> Unit,
    onOpenSimpleChatPromptSettings: () -> Unit = {},
    onOpenRecommendationUpdate: () -> Unit = {},
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
            onOpenSimpleChatPromptSettings = onOpenSimpleChatPromptSettings,
            onOpenRecommendationUpdate = onOpenRecommendationUpdate,
            modifier = scaffoldModifier,
        )
    }
}
