package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationTask
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationStatus

/** 无状态 surface 宿主；模型和交互生命周期由聊天 ViewModel 管理。 */
@Composable
internal fun ChatA2uiContent(
    surfaces: List<A2uiSurfaceModel>,
    failed: Boolean,
    tasks: List<A2uiGenerationTask> = emptyList(),
    retryEnabled: Boolean = false,
    onRetry: (String) -> Unit = {},
) {
    ChatA2uiTheme {
        Column(Modifier.fillMaxWidth()) {
            surfaces.forEach { surface ->
                key(surface.id) {
                    A2uiSurface(
                        surfaceModel = surface,
                        modifier = Modifier.fillMaxWidth(),
                        errorContent = { A2uiError() },
                    )
                }
            }
            tasks.filter { it.status != A2uiGenerationStatus.COMPLETED }.forEach { task ->
                key(task.callId) { A2uiTaskStatus(task, retryEnabled, onRetry) }
            }
            if (failed && tasks.isEmpty()) A2uiError()
        }
    }
}

/** 创建占位与错误共用原卡片位置；重试事件交给 Route/ViewModel。 */
@Composable
private fun A2uiTaskStatus(task: A2uiGenerationTask, retryEnabled: Boolean, onRetry: (String) -> Unit) {
    val loading = task.status == A2uiGenerationStatus.PENDING
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(when (task.status) {
                A2uiGenerationStatus.PENDING -> R.string.chat_a2ui_generating
                A2uiGenerationStatus.CANCELLED -> R.string.chat_a2ui_cancelled
                else -> R.string.chat_a2ui_failed
            }), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (!loading) TextButton(enabled = retryEnabled, onClick = { onRetry(task.callId) }) {
                Text(stringResource(R.string.chat_a2ui_retry))
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun A2uiTaskStatesPreview() {
    AsssistantaiTheme {
        ChatA2uiContent(emptyList(), failed = false, retryEnabled = true,
            tasks = listOf(A2uiGenerationStatus.PENDING, A2uiGenerationStatus.FAILED, A2uiGenerationStatus.CANCELLED)
                .map { A2uiGenerationTask(it.name, it.name, "fixture", "fixture", setOf("Image"), it) })
    }
}

@Composable
private fun A2uiError() {
    Text(stringResource(R.string.chat_a2ui_failed), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun A2uiErrorPreview() {
    AsssistantaiTheme { ChatA2uiContent(emptyList(), failed = true) }
}
