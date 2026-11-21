package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun ChatbotPromptSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatbotPromptViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceScaffold(
        title = stringResource(R.string.voice_prompt_presets),
        onBack = onBack,
    ) { scaffoldModifier ->
        ChatbotPromptSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}
