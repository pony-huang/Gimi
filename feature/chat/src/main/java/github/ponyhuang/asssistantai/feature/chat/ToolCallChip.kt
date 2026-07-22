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
 * 工具名展示：MCP 工具注册名带 `mcp_<服务器id前8位>_` 前缀（见 McpToolRegistry），
 * 哈希前缀对用户无意义，展示时剥掉，只留远端工具本身的可读名。
 */
private val McpNamePrefix = Regex("^mcp_[A-Za-z0-9_-]{8}_")

internal fun toolDisplayName(rawName: String): String = rawName.replace(McpNamePrefix, "")

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
            text = "${toolDisplayName(call.name)}${call.argsSummary}",
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
                text = "${toolDisplayName(response.name)} ✓",
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
