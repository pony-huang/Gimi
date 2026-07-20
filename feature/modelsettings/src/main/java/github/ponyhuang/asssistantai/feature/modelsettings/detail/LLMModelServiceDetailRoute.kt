package github.ponyhuang.asssistantai.feature.modelsettings.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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

@Composable
fun LLMModelServiceDetailRoute(
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
        viewModel.onAction(ModelServiceDetailAction.Load(serviceId))
    }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        val message = when (notice) {
            ModelServiceDetailNotice.ServiceNotFound -> serviceNotFoundMessage
            ModelServiceDetailNotice.ConnectionSucceeded -> connectionSucceededMessage
            ModelServiceDetailNotice.ConnectionFailed -> connectionFailedMessage
            is ModelServiceDetailNotice.ModelsSynchronized -> context.getString(
                R.string.modelsettings_notice_models_synchronized,
                notice.count,
            )
            ModelServiceDetailNotice.ModelSynchronizationFailed -> modelsSyncFailedMessage
            ModelServiceDetailNotice.AgentMutationBlocked -> agentBlockedMessage
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onAction(ModelServiceDetailAction.NoticeConsumed)
    }

    LaunchedEffect(state.shouldClose) {
        if (state.shouldClose) {
            onBack()
            viewModel.onAction(ModelServiceDetailAction.CloseConsumed)
        }
    }

    ModelServiceDetailScreen(
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
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, noBrowserMessage, Toast.LENGTH_SHORT).show()
    }
}
