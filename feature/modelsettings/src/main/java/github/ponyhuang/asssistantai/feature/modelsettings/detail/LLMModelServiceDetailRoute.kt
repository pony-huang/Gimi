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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LLMModelServiceDetailRoute(
    serviceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelServiceDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(serviceId) {
        viewModel.onAction(ModelServiceDetailAction.Load(serviceId))
    }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice.message(), Toast.LENGTH_SHORT).show()
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
        onOpenUrl = { url, missingMessage -> context.openUrl(url, missingMessage) },
        modifier = modifier,
    )
}

private fun ModelServiceDetailNotice.message(): String = when (this) {
    ModelServiceDetailNotice.ServiceNotFound -> "未找到该服务"
    ModelServiceDetailNotice.ConnectionSucceeded -> "检测成功"
    ModelServiceDetailNotice.ConnectionFailed -> "检测失败"
    is ModelServiceDetailNotice.ModelsSynchronized -> "已同步远端：$count 条"
    ModelServiceDetailNotice.ModelSynchronizationFailed -> "同步远端模型失败"
    ModelServiceDetailNotice.AgentMutationBlocked -> "Agent 任务进行中，请先停止任务后再修改。"
}

private fun Context.openUrl(url: String, missingMessage: String) {
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
        Toast.makeText(this, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}
