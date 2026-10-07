package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 输入栏上方的本地引擎加载反馈，失败时允许重试，模型选择器仍可用于切换。 */
@Composable
internal fun LocalModelLoadPanel(failed: Boolean, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!failed) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(if (failed) R.string.chat_local_model_load_failed else R.string.chat_local_model_loading),
                    style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(if (failed) R.string.chat_local_model_load_failed_hint else R.string.chat_local_model_loading_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_local_model_retry)) }
        }
    }
}

@Preview(name = "Local loading light", widthDp = 393, showBackground = true)
@Preview(name = "Local loading dark", widthDp = 393, uiMode = 0x20, showBackground = true)
@Composable
private fun LocalModelLoadingPreview() {
    AsssistantaiTheme { LocalModelLoadPanel(failed = false, onRetry = {}) }
}

@Preview(name = "Local load failed", widthDp = 393, showBackground = true)
@Composable
private fun LocalModelFailedPreview() {
    AsssistantaiTheme { LocalModelLoadPanel(failed = true, onRetry = {}) }
}
