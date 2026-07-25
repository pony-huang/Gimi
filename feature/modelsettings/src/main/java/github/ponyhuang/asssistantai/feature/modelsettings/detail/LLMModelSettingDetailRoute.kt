package github.ponyhuang.asssistantai.feature.modelsettings.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.feature.modelsettings.R
import androidx.core.net.toUri

@Composable
fun LLMModelSettingDetailRoute(
    serviceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelServiceDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val noBrowserMessage = stringResource(R.string.modelsettings_no_browser)
    val serviceNotFoundMessage = stringResource(R.string.modelsettings_notice_service_not_found)
    val connectionSucceededMessage = stringResource(R.string.modelsettings_notice_connection_succeeded)
    val connectionFailedMessage = stringResource(R.string.modelsettings_notice_connection_failed)
    val modelsSyncFailedMessage = stringResource(R.string.modelsettings_notice_models_sync_failed)
    val agentBlockedMessage = stringResource(R.string.modelsettings_agent_mutation_blocked)

    LaunchedEffect(serviceId) {
        viewModel.onAction(LLmModelSettingDetailAction.Load(serviceId))
    }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        val message = when (notice) {
            LLMModelSettingDetailNotice.SettingNotFoundLLM -> serviceNotFoundMessage
            LLMModelSettingDetailNotice.ConnectionSucceeded -> connectionSucceededMessage
            LLMModelSettingDetailNotice.ConnectionFailed -> connectionFailedMessage
            is LLMModelSettingDetailNotice.ModelsSynchronized -> context.getString(
                R.string.modelsettings_notice_models_synchronized,
                notice.count,
            )
            LLMModelSettingDetailNotice.LLMModelSynchronizationFailed -> modelsSyncFailedMessage
            LLMModelSettingDetailNotice.AgentMutationBlocked -> agentBlockedMessage
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onAction(LLmModelSettingDetailAction.NoticeConsumed)
    }

    LaunchedEffect(state.shouldClose) {
        if (state.shouldClose) {
            onBack()
            viewModel.onAction(LLmModelSettingDetailAction.CloseConsumed)
        }
    }

    LLMModelSettingDetailScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenUrl = { url, missingMessage -> context.openUrl(url, missingMessage, noBrowserMessage) },
        modifier = modifier,
    )
}

private fun Context.openUrl(url: String, missingMessage: String, noBrowserMessage: String) {
    if (url.isBlank()) {
        Toast.makeText(this, missingMessage, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, noBrowserMessage, Toast.LENGTH_SHORT).show()
    }
}
