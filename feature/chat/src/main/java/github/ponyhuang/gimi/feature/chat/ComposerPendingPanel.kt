package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    /** 挂起的工具授权请求。 */
    data class Confirmation(val request: PendingToolConfirmation) : PendingComposerAction
    /** 挂起的选项请求。 */
    data class Choice(val request: UserInputRequest) : PendingComposerAction
    /** 挂起的自由文本请求。 */
    data class TextInput(val request: UserInputRequest) : PendingComposerAction

    /** AnimatedContent 的 contentKey：请求变化（尤其 callId 变化）时触发内容切换。 */
    fun key(): String = when (this) {
        is Confirmation -> "confirmation-${request.confirmationCallId}"
        is Choice -> "choice-${request.callId}"
        is TextInput -> "input-${request.callId}"
    }
}

/** 选项区域最多展示五行，长按仅打开全文浮层，不提交选择。 */
@Composable
internal fun PendingChoicePanel(
    request: UserInputRequest,
    onRespond: (String) -> Unit,
) {
    PendingPanelContainer(scrollable = false) {
        PendingPanelHeader(text = stringResource(R.string.chat_choice_request_title))
        if (request.message.isNotBlank()) PendingRequestMessage(request.message)
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.heightIn(max = 272.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(request.options.size) { index ->
                val option = request.options[index]
                var showFullText by remember(request.callId, index) { mutableStateOf(false) }
                Box {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                .combinedClickable(
                                    onClick = { onRespond(option) },
                                    onLongClickLabel = stringResource(R.string.chat_choice_show_full_text),
                                    onLongClick = { showFullText = true },
                                ).padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(option, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (showFullText) {
                        androidx.compose.ui.window.Popup(
                            alignment = Alignment.BottomCenter,
                            onDismissRequest = { showFullText = false },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                        ) {
                            Surface(
                                modifier = Modifier.padding(16.dp).widthIn(max = 360.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 6.dp,
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                        Text(option, modifier = Modifier.heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState()),
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                    TextButton(onClick = { showFullText = false }, modifier = Modifier.align(Alignment.End)) {
                                        Text(stringResource(R.string.chat_choice_close_full_text))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 内容请求直接复用聊天胶囊的录音、转写与输入生命周期，草稿按请求隔离。 */
@Composable
internal fun PendingInputPanel(
    request: UserInputRequest,
    onRespond: (String) -> Unit,
    isVoiceInputAvailable: Boolean = false,
    onTranscribeVoice: VoiceTranscriptionRequest = { _, _, _, complete ->
        complete(Result.failure(IllegalStateException("transcription not configured")))
    },
    voiceTranscriptionMode: VoiceInputMode? = null,
    onCancelVoiceTranscription: () -> Unit = {},
) {
    var draft by remember(request.callId) { mutableStateOf(MessageData()) }
    ChatComposer(
        messageData = draft,
        onMessageDataChange = { draft = it },
        onSendClick = { data, onResult ->
            onRespond(data.text.trim())
            draft = MessageData()
            onResult(ChatSubmissionResult.ACCEPTED)
        },
        onStopClick = {},
        isGenerating = false,
        attachmentsEnabled = false,
        isVoiceInputAvailable = isVoiceInputAvailable,
        onTranscribeVoice = onTranscribeVoice,
        voiceTranscriptionMode = voiceTranscriptionMode,
        onCancelVoiceTranscription = onCancelVoiceTranscription,
        voiceInputContextKey = request.callId,
        headerContent = {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PendingPanelHeader(stringResource(R.string.chat_input_request_title))
                if (request.message.isNotBlank()) PendingRequestMessage(request.message)
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    )
}

/** 长提示拥有独立滚动区域，输入控件不随提示滚出视口。 */
@Composable
private fun PendingRequestMessage(message: String) {
    Text(message, modifier = Modifier.fillMaxWidth().heightIn(max = 128.dp)
        .verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
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
 * 面板容器：**取代**胶囊本体时复刻其几何 —— 外边距 12/8、28dp 圆角、
 * surfaceContainer 中性色，并自带 IME / 导航栏内边距（与 ChatComposer 外层一致），
 * 让面板就是输入栏。内容超高一屏时内部滚动，避免面板无限增高。
 */
@Composable
private fun PendingPanelContainer(
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp)
            .fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            // 与胶囊同为中性容器色：面板是输入栏的替身，不能比胶囊更“重”。
            color = chatCapsuleColor(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .then(if (scrollable) Modifier.heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()) else Modifier)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
            style = MaterialTheme.typography.titleSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 授权面板操作按钮的紧凑内边距：面板属于输入区，按钮高度向胶囊靠拢。 */
private val PanelButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 390, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
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
                        "选项 A（推荐）：这是一条超过一行的长选项，长按可查看并复制完整内容。",
                        "另一个普通单选选项",
                        "不带说明的最小形态",
                        "第四个选项",
                        "第五个选项",
                        "滚动后显示第六个选项",
                        "滚动后显示第七个选项",
                    ),
                ),
                onRespond = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 390, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PendingInputPanelPreview() {
    AsssistantaiTheme {
        PendingInputPanel(
            request = UserInputRequest(
                callId = "call-input-1",
                toolName = "adk_request_input",
                kind = UserInputKind.FREE_TEXT,
                message = "请补充以下信息：\n" + "请说明计划、日期、参与人员及注意事项，以便继续整理。\n".repeat(8),
            ),
            onRespond = {},
            isVoiceInputAvailable = true,
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
                originalCallId = "original-call-1",
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
                originalCallId = "original-call-2",
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
