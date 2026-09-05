package github.ponyhuang.gimi.feature.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import kotlinx.coroutines.delay

/** 胶囊态：底部居中悬浮胶囊 + 可选的提示文案（配置缺失/会话忙/链路错误）。 */
@Composable
internal fun AssistantCapsuleOverlay(
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
 * 聊天页同款视觉的胶囊输入条：文本框 + 麦克风 + 发送/关闭。
 *
 * @property docked true 嵌在面板底部（全宽），false 为悬浮胶囊（约 82% 宽、带阴影）。
 * @property onDismiss 关闭整个助手界面；悬浮胶囊传入（无输入时以 ✕ 兜底退出，
 *   避免「正在聆听」卡死），面板态传 null（关闭由头部操作行承担）。
 */
@Composable
internal fun AssistantCapsuleBar(
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
