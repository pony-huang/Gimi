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
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

/** 将云端记忆历史状态接入导航和短暂 UI 提示。 */
@Composable
fun MemoryHistoryRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val noticeMessage = when (state.notice) {
        MemoryHistoryNotice.Deleted -> stringResource(R.string.memory_history_deleted)
        MemoryHistoryNotice.FeedbackSubmitted -> stringResource(R.string.memory_history_feedback_submitted)
        MemoryHistoryNotice.OperationFailed -> stringResource(R.string.memory_history_operation_failed)
        null -> null
    }
    LaunchedEffect(state.notice, noticeMessage) {
        val message = noticeMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onAction(MemoryHistoryAction.ConsumeNotice)
    }
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
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
