package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.UserInputKind
import github.ponyhuang.gimi.domain.conversation.model.UserInputRequest
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 停靠在输入栏中的挂起操作面板：Agent 等待用户答复（授权 / 内容输入 / 选项选择）时，
 * 面板**取代**输入胶囊本体 —— 问题与操作就是输入栏，答复后胶囊恢复，与商业聊天
 * 应用“操作内嵌在输入栏”的形态一致。
 *
 * 容器复刻胶囊几何（外边距、收放留白、28dp 圆角、surfaceContainer 中性色），
 * 保证面板与胶囊是同一个位置的同一样东西，而不是消息流里多出的卡片。
 */

/**
 * 输入栏当前被哪种挂起操作取代；`null` 表示正常胶囊。
 * 授权与输入请求同时挂起时授权优先（与确认卡片的旧顺序一致）。
 */
internal sealed interface PendingComposerAction {
    data class Confirmation(val request: PendingToolConfirmation) : PendingComposerAction
    data class Choice(val request: UserInputRequest) : PendingComposerAction
    data class TextInput(val request: UserInputRequest) : PendingComposerAction

    /** AnimatedContent 的 contentKey：请求变化（尤其 callId 变化）时触发内容切换。 */
    fun key(): String = when (this) {
        is Confirmation -> "confirmation-${request.confirmationCallId}"
        is Choice -> "choice-${request.callId}"
        is TextInput -> "input-${request.callId}"
    }
}

/** 挂起的选项选择请求（`get_user_choice`）→ 编号选项列表，点选即答复。 */
@Composable
internal fun PendingChoicePanel(
    request: UserInputRequest,
    onRespond: (String) -> Unit,
) {
    PendingPanelContainer {
        PendingPanelHeader(text = request.message.ifBlank {
            stringResource(R.string.chat_input_request_title)
        })
        request.options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRespond(option) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 挂起的内容输入请求（`adk_request_input`）→ 紧凑输入框，随打随发。 */
@Composable
internal fun PendingInputPanel(
    request: UserInputRequest,
    onRespond: (String) -> Unit,
) {
    PendingPanelContainer {
        PendingPanelHeader(text = request.message.ifBlank {
            stringResource(R.string.chat_input_request_title)
        })
        var reply by remember(request.callId) { mutableStateOf("") }
        OutlinedTextField(
            value = reply,
            onValueChange = { reply = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.chat_input_request_hint)) },
            shape = RoundedCornerShape(16.dp),
            maxLines = 3,
            trailingIcon = {
                IconButton(
                    onClick = {
                        onRespond(reply.trim())
                        reply = ""
                    },
                    enabled = reply.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_input_request_send),
                    )
                }
            },
        )
    }
}

/**
 * 挂起的工具授权请求 → 工具名 / 描述 / 参数摘要 + 允许 / 拒绝 / 总是允许。
 *
 * 参数与描述限制行数：面板属于输入区，高度必须可控，完整内容以工具 chip 与
 * 会话历史为准。
 */
@Composable
internal fun PendingConfirmationPanel(
    request: PendingToolConfirmation,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onAlwaysAllow: () -> Unit,
) {
    PendingPanelContainer {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(R.string.chat_tool_confirmation_title),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            toolDisplayName(request.toolName),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (request.description.isNotBlank()) {
            Text(
                request.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (request.arguments.isNotBlank()) {
            Text(
                request.arguments,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onAlwaysAllow,
                contentPadding = PanelButtonPadding,
            ) {
                Text(stringResource(R.string.chat_action_always_allow), maxLines = 1)
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onReject,
                contentPadding = PanelButtonPadding,
            ) {
                Text(stringResource(R.string.chat_action_reject), maxLines = 1)
            }
            Button(
                onClick = onConfirm,
                contentPadding = PanelButtonPadding,
            ) {
                Text(stringResource(R.string.chat_action_allow), maxLines = 1)
            }
        }
    }
}

/**
 * 面板容器：**取代**胶囊本体时复刻其几何 —— 外边距 12/8、收放留白 20、28dp 圆角、
 * surfaceContainer 中性色，并自带 IME / 导航栏内边距（与 ChatComposer 外层一致），
 * 让面板就是输入栏。内容超高一屏时内部滚动，避免面板无限增高。
 */
@Composable
private fun PendingPanelContainer(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = ComposerCollapsedHorizontalInset)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            // 与胶囊同为中性容器色：面板是输入栏的替身，不能比胶囊更“重”。
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun PendingPanelHeader(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 授权面板操作按钮的紧凑内边距：面板属于输入区，按钮高度向胶囊靠拢。 */
private val PanelButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)

@Preview(showBackground = true)
@Composable
private fun PendingChoicePanelPreview() {
    AsssistantaiTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PendingChoicePanel(
                request = UserInputRequest(
                    callId = "call-choice-1",
                    toolName = "get_user_choice",
                    kind = UserInputKind.CHOICE,
                    message = "这是一条测试用的单选题，用来看选项列表和说明文字的显示效果。",
                    options = listOf(
                        "选项 A（推荐）",
                        "另一个普通单选选项",
                        "不带说明的最小形态",
                    ),
                ),
                onRespond = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PendingInputPanelPreview() {
    AsssistantaiTheme {
        PendingInputPanel(
            request = UserInputRequest(
                callId = "call-input-1",
                toolName = "adk_request_input",
                kind = UserInputKind.FREE_TEXT,
                message = "请问出发日期是哪一天？",
            ),
            onRespond = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PendingConfirmationPanelPreview() {
    AsssistantaiTheme {
        PendingConfirmationPanel(
            request = PendingToolConfirmation(
                confirmationCallId = "call-1",
                toolName = "web_search",
                description = "联网搜索工具，用于查询实时信息",
                arguments = "query: 上海今天天气\nlimit: 5",
            ),
            onConfirm = {},
            onReject = {},
            onAlwaysAllow = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PendingConfirmationPanelMinimalPreview() {
    AsssistantaiTheme {
        PendingConfirmationPanel(
            request = PendingToolConfirmation(
                confirmationCallId = "call-2",
                toolName = "unknown_tool",
                description = "",
                arguments = "",
            ),
            onConfirm = {},
            onReject = {},
            onAlwaysAllow = {},
        )
    }
}
