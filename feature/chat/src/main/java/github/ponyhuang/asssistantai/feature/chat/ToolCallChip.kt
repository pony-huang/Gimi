package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.conversation.model.FunctionCallView
import github.ponyhuang.asssistantai.domain.conversation.model.FunctionResponseView
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme

/**
 * 工具调用 chip — 在气泡上方展示一行 `name(args)` 风格的小标签。
 *
 * 设计参考：adk-web 的 `<app-hover-info-button>` chip 行（`event-content.component.html:28-162`）。
 */
@Composable
fun ToolCallChip(
    call: FunctionCallView,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = "${call.name}${call.argsSummary}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * 工具响应 chip — 更小、更淡，用于表示 `name` 已经返回结果。
 */
@Composable
fun ToolResponseChip(
    response: FunctionResponseView,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(
                text = "${response.name} ✓",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ── Preview ──────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ToolCallChipPreview() {
    AsssistantaiTheme {
        Row(modifier = Modifier.padding(8.dp)) {
            ToolCallChip(call = FunctionCallView(id = "1", name = "getCurrentTime", argsSummary = "(city=\"北京\")"))
            ToolResponseChip(response = FunctionResponseView(id = "1", name = "getCurrentTime"))
        }
    }
}
