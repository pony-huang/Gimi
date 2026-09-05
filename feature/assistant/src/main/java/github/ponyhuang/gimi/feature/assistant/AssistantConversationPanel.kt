package github.ponyhuang.gimi.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessage
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessageAuthor
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.ui.chatcontent.ChatBubbleRole
import github.ponyhuang.gimi.ui.chatcontent.ChatMessageBubble
import github.ponyhuang.gimi.ui.chatcontent.ChatTextContent
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 面板态：通栏底部面板（顶部大圆角 + 拖动手柄），消息列表与聊天页同一渲染组件，
 * 底部嵌入胶囊输入条。下滑手柄收回胶囊态，会话继续在后台执行。
 * 三个宿主（应用内 / 悬浮窗 / 锁屏）使用完全相同的面板视觉，保证体验一致。
 */
@Composable
internal fun AssistantConversationPanel(
    state: AssistantSessionState,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onMicToggle: () -> Unit,
    onTextSubmit: (String) -> Unit,
    recording: Boolean,
    audioLevel: Float,
    onInputFocusChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    overlayIme: Boolean,
    modifier: Modifier = Modifier,
) {
    // 面板高度上限约屏高 2/3；消息列表上限扣除手柄/操作行/输入条的固定预留。
    val panelMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 2f / 3f
    val listMaxHeight = panelMaxHeight - 170.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .heightIn(max = panelMaxHeight)
                // 悬浮窗宿主没有 OnBackPressedDispatcher，直接拦截返回键收回面板。
                .then(
                    if (overlayIme) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                                onCollapse()
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            AssistantPanelContent(
                state = state,
                listMaxHeight = listMaxHeight,
                onOpenChat = onOpenChat,
                onDismiss = onDismiss,
                onCollapse = onCollapse,
                onMicToggle = onMicToggle,
                onTextSubmit = onTextSubmit,
                recording = recording,
                audioLevel = audioLevel,
                onInputFocusChange = onInputFocusChange,
                overlayIme = overlayIme,
            )
        }
    }
}

@Composable
private fun AssistantPanelContent(
    state: AssistantSessionState,
    listMaxHeight: Dp,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
    onCollapse: () -> Unit,
    onMicToggle: () -> Unit,
    onTextSubmit: (String) -> Unit,
    recording: Boolean,
    audioLevel: Float,
    onInputFocusChange: (Boolean) -> Unit,
    overlayIme: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 头部单行布局：拖动手柄居中、操作按钮靠右，避免按钮挤在一起。
        Box(modifier = Modifier.fillMaxWidth()) {
            AssistantDragHandle(
                onCollapse = onCollapse,
                modifier = Modifier.align(Alignment.Center),
            )
            AssistantPanelActions(
                onOpenChat = onOpenChat,
                onDismiss = onDismiss,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        AssistantMessageList(
            state = state,
            modifier = Modifier.padding(top = 2.dp).heightIn(max = listMaxHeight),
        )
        Spacer(Modifier.height(8.dp))
        AssistantCapsuleBar(
            listening = state.phase == AssistantSessionPhase.LISTENING,
            transcribing = state.phase == AssistantSessionPhase.TRANSCRIBING,
            recording = recording,
            audioLevel = audioLevel,
            docked = true,
            overlayIme = overlayIme,
            onMicToggle = onMicToggle,
            onTextSubmit = onTextSubmit,
            onInputFocusChange = onInputFocusChange,
            onDismiss = null,
            // 面板背景延伸到导航栏后面（沉浸式），输入条自身避开手势条。
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 拖动手柄：下滑超过阈值收回面板。 */
@Composable
private fun AssistantDragHandle(onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    val collapseDescription = stringResource(R.string.assistant_panel_collapse)
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .semantics { contentDescription = collapseDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                .pointerInput(onCollapse) {
                    // 累计下滑距离超过阈值才收回，避免轻微滑动误触。
                    var accumulated = 0f
                    val threshold = 48.dp.toPx()
                    detectVerticalDragGestures(
                        onDragEnd = { accumulated = 0f },
                        onDragCancel = { accumulated = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount
                        if (accumulated > threshold) {
                            accumulated = 0f
                            onCollapse()
                        }
                    }
                },
        )
    }
}

/** 面板右上角的操作行：打开聊天、关闭。 */
@Composable
private fun AssistantPanelActions(
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = 12.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenChat, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = stringResource(R.string.assistant_action_open_chat),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.assistant_action_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 面板消息列表：用户/助手/错误三类气泡，渲染与聊天页完全一致。 */
@Composable
private fun AssistantMessageList(
    state: AssistantSessionState,
    modifier: Modifier = Modifier,
) {
    if (state.messages.isEmpty()) return
    val listState = rememberLazyListState()
    val lastMessage = state.messages.last()
    // 消息数量或末条文本长度变化都触发跟随（流式增长时不丢最新内容）。
    LaunchedEffect(state.messages.size, lastMessage.text.length) {
        listState.animateScrollToItem(state.messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            AssistantMessageRow(message)
        }
    }
}

@Composable
private fun AssistantMessageRow(message: AssistantMessage) {
    when {
        message.isError -> ChatMessageBubble(role = ChatBubbleRole.ASSISTANT) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.small,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        message.author == AssistantMessageAuthor.USER -> ChatMessageBubble(role = ChatBubbleRole.USER) {
            ChatTextContent(
                text = message.text,
                partial = false,
                chunkChannel = null,
                fillAvailableWidth = false,
            )
        }
        else -> ChatMessageBubble(role = ChatBubbleRole.ASSISTANT) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (message.streaming && message.text.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.assistant_status_generating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // 全文桥接流式：助手协调器只发布累积全文，ChatTextContent 内部按前缀 diff 增量渲染。
                    ChatTextContent(
                        text = message.text,
                        partial = message.streaming,
                        chunkChannel = null,
                    )
                }
                if (message.toolNames.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.assistant_tool_running,
                            message.toolNames.joinToString("、"),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AssistantConversationPanelEmptyPreview() {
    AsssistantaiTheme {
        AssistantConversationPanel(
            state = AssistantSessionState(),
            onDismiss = {},
            onOpenChat = {},
            onMicToggle = {},
            onTextSubmit = {},
            recording = false,
            audioLevel = 0f,
            onInputFocusChange = {},
            onCollapse = {},
            overlayIme = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssistantConversationPanelWithMessagesPreview() {
    AsssistantaiTheme {
        AssistantConversationPanel(
            state = AssistantSessionState(
                phase = AssistantSessionPhase.GENERATING,
                messages = listOf(
                    AssistantMessage(1, AssistantMessageAuthor.USER, "帮我看看今天的日程"),
                    AssistantMessage(
                        id = 2,
                        author = AssistantMessageAuthor.ASSISTANT,
                        text = "你今天下午三点有一个项目评审会议。",
                        toolNames = listOf("calendar"),
                    ),
                ),
            ),
            onDismiss = {},
            onOpenChat = {},
            onMicToggle = {},
            onTextSubmit = {},
            recording = false,
            audioLevel = 0f,
            onInputFocusChange = {},
            onCollapse = {},
            overlayIme = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssistantConversationPanelErrorPreview() {
    AsssistantaiTheme {
        AssistantConversationPanel(
            state = AssistantSessionState(
                messages = listOf(
                    AssistantMessage(1, AssistantMessageAuthor.USER, "明天提醒我买菜"),
                    AssistantMessage(
                        id = 2,
                        author = AssistantMessageAuthor.ASSISTANT,
                        text = "任务执行失败，请稍后重试。",
                        isError = true,
                    ),
                ),
            ),
            onDismiss = {},
            onOpenChat = {},
            onMicToggle = {},
            onTextSubmit = {},
            recording = false,
            audioLevel = 0f,
            onInputFocusChange = {},
            onCollapse = {},
            overlayIme = false,
        )
    }
}
