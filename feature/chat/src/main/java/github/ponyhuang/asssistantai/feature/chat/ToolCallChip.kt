package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.conversation.model.FunctionCallView
import github.ponyhuang.asssistantai.domain.conversation.model.FunctionResponseView
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme

/**
 * 工具名展示：MCP 工具由 [McpToolsetRegistry] 通过 [McpToolset] 接入，
 * 使用远端工具原始名称；保留此前缀正则以兼容历史工具名格式。
 */
private val McpNamePrefix = Regex("^mcp_[A-Za-z0-9_-]{8}_")

internal fun toolDisplayName(rawName: String): String = rawName.replace(McpNamePrefix, "")

/**
 * 工具调用 chip — 一次调用只占一行，并内联表达执行状态（优先级从高到低）：
 *
 * - [completed]（响应已回）：尾部 ✓，容器转为 tertiaryContainer，与旧的 response chip 同色系；
 * - [rejected]（用户拒绝确认，或任务已结束但响应始终未回）：尾部 error 色 ✗；
 * - [awaitingConfirmation]（确认卡片挂起中）：尾部盾牌图标，提示"在等你决定"而非执行中；
 * - [inProgress]（流式中、响应未回）：尾部 10dp 进度圈；
 * - 其余（异常历史数据等）：纯名称。
 *
 * 参数摘要 [FunctionCallView.argsSummary] 可能很长，单行省略，宽度由调用方约束
 * （一般在 Row 里传 `Modifier.weight(1f, fill = false)`）。
 */
@Composable
fun ToolCallChip(
    call: FunctionCallView,
    completed: Boolean,
    inProgress: Boolean,
    rejected: Boolean,
    awaitingConfirmation: Boolean,
    modifier: Modifier = Modifier,
) {
    ToolActivityChip(
        text = "${toolDisplayName(call.name)}${call.argsSummary}",
        completed = completed,
        inProgress = inProgress,
        rejected = rejected,
        awaitingConfirmation = awaitingConfirmation,
        modifier = modifier,
    )
}

/**
 * 未匹配到调用的孤立工具响应 chip（id 为空或响应先于调用到达的兜底），按已完成样式渲染。
 */
@Composable
fun ToolResponseChip(
    response: FunctionResponseView,
    modifier: Modifier = Modifier,
) {
    ToolActivityChip(
        text = toolDisplayName(response.name),
        completed = true,
        inProgress = false,
        rejected = false,
        awaitingConfirmation = false,
        modifier = modifier,
    )
}

@Composable
private fun ToolActivityChip(
    text: String,
    completed: Boolean,
    inProgress: Boolean,
    rejected: Boolean,
    awaitingConfirmation: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (completed) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (completed) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                completed -> {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
                rejected -> {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp),
                    )
                }
                awaitingConfirmation -> {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                inProgress -> {
                    Spacer(Modifier.width(5.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                    )
                }
            }
        }
    }
}

// ── Preview ──────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ToolCallChipPreview() {
    AsssistantaiTheme {
        Row(modifier = Modifier.padding(8.dp)) {
            ToolCallChip(
                call = FunctionCallView(id = "1", name = "getCurrentTime", argsSummary = "(city=\"北京\")"),
                completed = false,
                inProgress = true,
                rejected = false,
                awaitingConfirmation = false,
            )
            ToolCallChip(
                call = FunctionCallView(id = "2", name = "getCurrentTime", argsSummary = "(city=\"北京\")"),
                completed = true,
                inProgress = false,
                rejected = false,
                awaitingConfirmation = false,
            )
            ToolCallChip(
                call = FunctionCallView(id = "3", name = "pick_contact", argsSummary = ""),
                completed = false,
                inProgress = false,
                rejected = true,
                awaitingConfirmation = false,
            )
        }
    }
}
