package github.ponyhuang.gimi.feature.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessage
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessageAuthor
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState

/** 助手面板在不同宿主中的密度规格。 */
enum class AssistantSurfaceMode {
    SHEET,
    OVERLAY,
    LOCK_SCREEN,
}

/**
 * 参考 Home Assistant assist 的会话面板：气泡、居中麦克风与聊天胶囊输入。
 *
 * 布局在垂直方向保持对称：标题居中，空状态把问候语、说话提示与麦克风整体居中，
 * 底部输入区复用聊天胶囊（圆角、容器底、文本+发送）。
 */
@Composable
fun AssistantSurface(
    state: AssistantSessionState,
    mode: AssistantSurfaceMode,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: () -> Unit,
    onMicToggle: () -> Unit = {},
    onTextSubmit: (String) -> Unit = {},
    recording: Boolean = false,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val compact = mode != AssistantSurfaceMode.SHEET
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (compact) 520.dp else 640.dp),
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 8.dp else 2.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(max = if (compact) 480.dp else 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistantHeader(
                state = state,
                onDismiss = onDismiss,
                onStop = onStop,
                onOpenChat = onOpenChat,
            )

            AssistantConversation(
                messages = state.messages,
                compact = compact,
                recording = recording,
                audioLevel = audioLevel,
                onMicToggle = onMicToggle,
                modifier = Modifier.weight(1f),
            )

            AssistantInputCapsule(onTextSubmit = onTextSubmit)
        }
    }
}

@Composable
private fun AssistantHeader(
    state: AssistantSessionState,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // 运行阶段不再展示标题，避免与右侧操作按钮重叠；错误以消息气泡呈现。
        // 仅保留“未配置模型”：该状态没有消息和操作按钮，标题是唯一反馈出口。
        if (state.phase == AssistantSessionPhase.MISSING_CONFIG) {
            Text(
                text = stringResource(R.string.assistant_status_missing_config),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.taskActive) {
                TextButton(onClick = onStop) {
                    Text(stringResource(R.string.assistant_action_stop))
                }
            }
            if (state.messages.isNotEmpty() || state.phase == AssistantSessionPhase.FOLLOW_UP_IDLE) {
                TextButton(onClick = onOpenChat) {
                    Text(stringResource(R.string.assistant_action_open_chat))
                }
            }
            if (state.messages.isNotEmpty()) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.assistant_action_close),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantConversation(
    messages: List<AssistantMessage>,
    compact: Boolean,
    recording: Boolean,
    audioLevel: Float,
    onMicToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) {
        // 空状态：问候语、说话提示与麦克风整体居中，麦克风即面板的中部主控。
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.assistant_input_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                if (recording) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(24) { index ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(
                                        if (index % 3 == 0) {
                                            8.dp + (12.dp * audioLevel.coerceIn(0f, 1f))
                                        } else {
                                            8.dp
                                        },
                                    ),
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.assistant_voice_idle_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                AssistantMicrophoneButton(
                    recording = recording,
                    compact = compact,
                    onClick = onMicToggle,
                )
            }
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = if (compact) 208.dp else 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            AssistantBubble(message, compact)
        }
    }
}

@Composable
private fun AssistantBubble(message: AssistantMessage, compact: Boolean) {
    val isUser = message.author == AssistantMessageAuthor.USER
    val container = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (compact) 360.dp else 480.dp)
                .background(container, RoundedCornerShape(18.dp))
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (message.streaming && message.text.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = content,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.assistant_status_generating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = content,
                    )
                }
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = content,
                    maxLines = if (compact) 6 else 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (message.toolNames.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.assistant_tool_running,
                        message.toolNames.joinToString("、"),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 复用聊天胶囊视觉的文本输入：28.dp 圆角、容器底色，内部为透明文本域加右侧发送按钮。
 * 底部输入区始终存在，与中部麦克风并列承载语音/文字两条输入路径。
 */
@Composable
private fun AssistantInputCapsule(
    onTextSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    fun submit() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            onTextSubmit(text)
            draft = ""
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
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
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { submit() },
                enabled = draft.isNotBlank(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.assistant_send),
                )
            }
        }
    }
}

@Composable
private fun AssistantMicrophoneButton(
    recording: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "assistantMic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micPulse",
    )
    val size = if (compact) 64.dp else 80.dp
    Surface(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = stringResource(
                    if (recording) R.string.assistant_voice_stop else R.string.assistant_voice_start,
                ),
                modifier = Modifier.size(if (compact) 32.dp else 40.dp),
            )
        }
    }
}
