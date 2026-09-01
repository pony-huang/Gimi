package github.ponyhuang.gimi.feature.memory

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

/** 将云端记忆历史状态接入导航和短暂 UI 提示。 */
@Composable
fun MemoryHistoryRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                MemoryHistoryEffect.Deleted -> R.string.memory_history_deleted
                MemoryHistoryEffect.FeedbackSubmitted -> R.string.memory_history_feedback_submitted
                MemoryHistoryEffect.OperationFailed -> R.string.memory_history_operation_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    PreferenceScaffold(
        title = stringResource(R.string.memory_history_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        MemoryHistoryScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = modifier.then(scaffoldModifier),
        )
    }
}
