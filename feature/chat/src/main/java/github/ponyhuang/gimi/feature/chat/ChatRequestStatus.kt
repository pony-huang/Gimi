package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 当前请求的展示状态；只派生界面，不改变历史错误或原请求快照。 */
internal data class ChatRequestStatus(val isRetrying: Boolean, val error: String? = null)

/** 只读取最近用户轮次的错误，取消没有错误正文时由界面提供通用说明。 */
internal fun ChatUiState.requestStatus(): ChatRequestStatus? {
    if (isRetryingFailedTurn) return ChatRequestStatus(isRetrying = true)
    if (failedTurn == null || isAgentRunning) return null
    val errors = (listItems.lastOrNull() as? ChatListItem.AssistantTurn)?.timeline?.segments.orEmpty()
        .filterIsInstance<TurnSegment.Answer>().mapNotNull { it.message.error }
    return ChatRequestStatus(isRetrying = false, error = errors.lastOrNull()?.takeIf(String::isNotBlank))
}

/** 当前错误由外部状态卡承载；保留历史错误与同一消息中的正文、工具和附件。 */
internal fun List<ChatListItem>.withoutCurrentRequestErrors(): List<ChatListItem> {
    val latestTurn = (lastOrNull() as? ChatListItem.AssistantTurn)?.timeline?.turnId ?: return this
    return map { item ->
        if (item !is ChatListItem.AssistantTurn || item.timeline.turnId != latestTurn) item
        else item.copy(timeline = item.timeline.copy(segments = item.timeline.segments.mapNotNull { segment ->
            if (segment !is TurnSegment.Answer || segment.message.error == null) segment
            else segment.message.copy(error = null).takeIf(Message::hasRequestContent)?.let { TurnSegment.Answer(it) }
        }))
    }
}

private fun Message.hasRequestContent(): Boolean = textParts.any { it.text.isNotBlank() } ||
    fileAttachments.isNotEmpty() || functionCalls.isNotEmpty() || functionResponses.isNotEmpty() || a2uiInteraction != null

/** 与输入胶囊独立，重试只作用于旧请求，编辑中的新草稿继续保留。 */
@Composable
internal fun ChatRequestStatusCard(status: ChatRequestStatus, onRetry: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("chat_request_status"),
        shape = RoundedCornerShape(16.dp),
        color = if (status.isRetrying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (status.isRetrying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (status.isRetrying) R.string.chat_request_retrying else R.string.chat_request_failed),
                    style = MaterialTheme.typography.labelLarge)
                Text(if (status.isRetrying) stringResource(R.string.chat_request_retry_draft_preserved)
                    else status.error ?: stringResource(R.string.chat_request_failed_fallback),
                    style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            TextButton(onClick = if (status.isRetrying) onStop else onRetry,
                modifier = Modifier.testTag(if (status.isRetrying) "request_retry_cancel" else "failed_turn_retry"),
                colors = ButtonDefaults.textButtonColors(contentColor = if (status.isRetrying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(if (status.isRetrying) R.string.chat_request_cancel else R.string.chat_failed_turn_retry))
            }
        }
    }
}

@Preview(name = "请求失败", showBackground = true, widthDp = 390)
@Preview(name = "请求失败 · 深色", showBackground = true, widthDp = 390, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RequestStatusPreview() {
    AsssistantaiTheme { ChatRequestStatusCard(ChatRequestStatus(false, "网络连接中断"), {}, {}) }
}
