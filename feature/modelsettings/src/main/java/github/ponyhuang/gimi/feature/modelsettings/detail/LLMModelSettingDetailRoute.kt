package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.modelsettings.R

@Composable
fun LLMModelSettingDetailRoute(
    serviceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelServiceDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val noBrowser = stringResource(R.string.modelsettings_no_browser)
    val notFound = stringResource(R.string.modelsettings_notice_service_not_found)
    val saved = stringResource(R.string.modelsettings_saved)
    val deleted = stringResource(R.string.modelsettings_deleted)
    val blocked = stringResource(R.string.modelsettings_agent_mutation_blocked)
    BackHandler { viewModel.onAction(LLMModelSettingDetailAction.Back) }
    LaunchedEffect(serviceId) { viewModel.onAction(LLMModelSettingDetailAction.Load(serviceId)) }
    LaunchedEffect(viewModel, onBack, notFound, saved, deleted, blocked) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LLMModelSettingDetailEffect.ShowToast -> Toast.makeText(context, when (effect.notice) {
                    is LLMModelSettingDetailNotice.ModelsRefreshed -> context.getString(R.string.modelsettings_notice_models_synchronized, effect.notice.count)
                    LLMModelSettingDetailNotice.SettingNotFoundLLM -> notFound
                    LLMModelSettingDetailNotice.Saved -> saved
                    LLMModelSettingDetailNotice.Deleted -> deleted
                    LLMModelSettingDetailNotice.AgentMutationBlocked -> blocked
                }, Toast.LENGTH_SHORT).show()
                LLMModelSettingDetailEffect.Close -> onBack()
            }
        }
    }
    LLMModelSettingDetailScreen(state, viewModel::onAction,
        onOpenUrl = { url, missing -> context.openUrl(url, missing, noBrowser) }, modifier = modifier)
}

private fun Context.openUrl(url: String, missing: String, noBrowser: String) {
    if (url.isBlank()) {
        Toast.makeText(this, missing, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, noBrowser, Toast.LENGTH_SHORT).show()
    }
}
