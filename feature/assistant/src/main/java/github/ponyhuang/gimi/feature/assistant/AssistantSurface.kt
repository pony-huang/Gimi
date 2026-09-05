package github.ponyhuang.gimi.feature.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessage
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessageAuthor
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.domain.assistant.model.shouldShowConversation
import github.ponyhuang.gimi.ui.chatcontent.ChatBubbleRole
import github.ponyhuang.gimi.ui.chatcontent.ChatMessageBubble
import github.ponyhuang.gimi.ui.chatcontent.ChatTextContent
import kotlinx.coroutines.delay

/** 助手界面在不同宿主中的承载方式。 */
enum class AssistantSurfaceMode {
    SHEET,
    OVERLAY,
    LOCK_SCREEN,
}

/**
 * 语音助手的两态界面：悬浮胶囊态与全宽对话面板态。
 *
 * 唤醒后先出现底部悬浮胶囊（输入框 + 麦克风 + 发送），指令就绪后展开为通栏对话面板，
 * 消息渲染复用 [ChatMessageBubble] / [ChatTextContent]，与聊天页保持同一套视觉。
 * 三种宿主（应用内 Sheet / 系统悬浮窗 / 锁屏 Activity）渲染同一 Composable。
 *
 * @param onInputFocusChange 输入框焦点变化回调；系统悬浮窗宿主借此切换窗口可获焦标志，
 *        让用户能在桌面上直接打字，其余宿主可忽略。
 */
@Composable
fun AssistantSurface(
    state: AssistantSessionState,
    mode: AssistantSurfaceMode,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onMicToggle: () -> Unit = {},
    onTextSubmit: (String) -> Unit = {},
    recording: Boolean = false,
    audioLevel: Float = 0f,
    onInputFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 用户下滑收回面板后的本地覆盖；新一轮指令就绪（shouldShowConversation 翻转）时自动恢复展开。
    var manuallyCollapsed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.shouldShowConversation) {
        if (state.shouldShowConversation) manuallyCollapsed = false
    }
    val expanded = state.shouldShowConversation && !manuallyCollapsed
    // 收回胶囊或关闭后通知宿主释放输入焦点（悬浮窗恢复不可获焦，避免拦截返回键/键盘）。
    LaunchedEffect(expanded) {
        if (!expanded) onInputFocusChange(false)
    }

    if (expanded) {
        AssistantConversationPanel(
            state = state,
            onDismiss = onDismiss,
            onOpenChat = onOpenChat,
            onMicToggle = onMicToggle,
            onTextSubmit = onTextSubmit,
            recording = recording,
            audioLevel = audioLevel,
            onInputFocusChange = onInputFocusChange,
            onCollapse = { manuallyCollapsed = true },
            overlayIme = mode == AssistantSurfaceMode.OVERLAY,
            modifier = modifier,
        )
    } else {
        AssistantCapsuleOverlay(
            state = state,
            onDismiss = onDismiss,
            onMicToggle = onMicToggle,
            onTextSubmit = onTextSubmit,
            recording = recording,
            audioLevel = audioLevel,
            onInputFocusChange = onInputFocusChange,
            overlayIme = mode == AssistantSurfaceMode.OVERLAY,
            modifier = modifier,
        )
    }
}

/** 胶囊态：底部居中悬浮胶囊 + 可选的提示文案（配置缺失/会话忙/链路错误）。 */
@Composable
private fun AssistantCapsuleOverlay(
    state: AssistantSessionState,
    onDismiss: () -> Unit,
    onMicToggle: () -> Unit,
    onTextSubmit: (String) -> Unit,
    recording: Boolean,
    audioLevel: Float,
    onInputFocusChange: (Boolean) -> Unit,
    overlayIme: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        // 悬浮胶囊需要避开手势条/导航栏；面板态的导航栏留白由面板内部沉浸式处理。
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 该阶段没有消息气泡可承载反馈，提示落在胶囊上方。
        val hint = when {
            state.configIssue != null -> stringResource(R.string.assistant_status_missing_config)
            state.phase == AssistantSessionPhase.BUSY -> stringResource(R.string.assistant_status_busy)
            else -> state.errorMessage
        }
        if (hint != null) {
            Text(
                text = hint,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        AssistantCapsuleBar(
            listening = state.phase == AssistantSessionPhase.LISTENING,
            transcribing = state.phase == AssistantSessionPhase.TRANSCRIBING,
            recording = recording,
            audioLevel = audioLevel,
            docked = false,
            overlayIme = overlayIme,
            onMicToggle = onMicToggle,
            onTextSubmit = onTextSubmit,
            onInputFocusChange = onInputFocusChange,
            onDismiss = onDismiss,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/**
 * 面板态：通栏底部面板（顶部大圆角 + 拖动手柄），消息列表与聊天页同一渲染组件，
 * 底部嵌入胶囊输入条。下滑手柄收回胶囊态，会话继续在后台执行。
 * 三个宿主（应用内 / 悬浮窗 / 锁屏）使用完全相同的面板视觉，保证体验一致。
 */
@Composable
private fun AssistantConversationPanel(
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

/**
 * 聊天页同款视觉的胶囊输入条：文本框 + 麦克风 + 发送/关闭。
 *
 * @property docked true 嵌在面板底部（全宽），false 为悬浮胶囊（约 82% 宽、带阴影）。
 * @property onDismiss 关闭整个助手界面；悬浮胶囊传入（无输入时以 ✕ 兜底退出，
 *   避免「正在聆听」卡死），面板态传 null（关闭由头部操作行承担）。
 */
@Composable
private fun AssistantCapsuleBar(
    listening: Boolean,
    transcribing: Boolean,
    recording: Boolean,
    audioLevel: Float,
    docked: Boolean,
    overlayIme: Boolean,
    onMicToggle: () -> Unit,
    onTextSubmit: (String) -> Unit,
    onInputFocusChange: (Boolean) -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            onTextSubmit(text)
            draft = ""
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(if (docked) 1f else 0.82f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (docked) 0.dp else 6.dp,
        tonalElevation = 0.dp,
    ) {
        // 悬浮窗宿主：等待窗口切换为可获焦后再拉起输入法（标志位切换异步生效）。
        LaunchedEffect(inputFocused) {
            if (overlayIme && inputFocused) {
                delay(150)
                if (inputFocused) keyboard?.show()
            }
        }
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                // 蓝牙链路正在采集指令：麦克风被语音管线占用，禁用输入只展示聆听状态。
                listening && !recording -> {
                    // 状态内容在胶囊剩余空间内居中，按钮统一靠右，避免挤在一侧。
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistantPulseDots()
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.assistant_listening_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                recording -> {
                    // 面板麦克风录音中：实时电平波形，再次点按麦克风即停止并转写提交。
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistantLevelBars(audioLevel = audioLevel)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.assistant_listening_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                transcribing -> {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.assistant_transcribing_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                else -> {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp)
                            .onFocusChanged {
                                inputFocused = it.isFocused
                                onInputFocusChange(it.isFocused)
                            }
                            .testTag("assistant_input_capsule"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (draft.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.assistant_input_placeholder),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onMicToggle,
                enabled = !listening && !transcribing,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(
                        if (recording) R.string.assistant_voice_stop else R.string.assistant_voice_start,
                    ),
                    tint = if (recording) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!listening && !recording && !transcribing && draft.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                FilledIconButton(
                    onClick = { submit() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.assistant_send),
                    )
                }
            } else if (onDismiss != null) {
                // 悬浮胶囊始终保留关闭出口：聆听/转写状态无法输入，没有它用户只能干等自动收起。
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.assistant_action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 蓝牙采集链路不透出实时电平，用错峰脉动圆点表达"正在聆听"。 */
@Composable
private fun AssistantPulseDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "assistantPulse")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(650, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 220),
                ),
                label = "assistantPulseDot$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
            )
        }
    }
}

/** 面板麦克风录音中的实时电平波形。 */
@Composable
private fun AssistantLevelBars(
    audioLevel: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(12) { index ->
            val boosted = if (index % 3 == 0) audioLevel else audioLevel * 0.5f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(8.dp + (14.dp * boosted.coerceIn(0f, 1f)))
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}
