package github.ponyhuang.asssistantai.feature.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionPhase

/** 宽屏下的浮层内容最大宽度。 */
private val OverlayMaxWidth = 560.dp

/**
 * 无状态助理浮层：状态经 [state] 进入，事件经 [onAction] 离开。
 * 遮罩、安全边距与窗口属性由宿主 Activity/主题负责。
 *
 * 展开内容与底部输入条合并为单个圆角容器：一层阴影、一条头发丝分割线，
 * 避免「两张卡片叠放」的割裂感；右侧操作键始终是全页唯一的实心主按钮。
 */
@Composable
fun AssistantOverlayScreen(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
    onOpenInChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelColor = MaterialTheme.colorScheme.surface
    val panelContentColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = OverlayMaxWidth)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = panelColor,
            contentColor = panelContentColor,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, panelContentColor.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("assistant_search_bar"),
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (state.shouldShowExpandedContent()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("assistant_expanded_content"),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = onOpenInChat,
                                modifier = Modifier.testTag("assistant_open_in_chat"),
                            ) {
                                Text(stringResource(R.string.assistant_open_in_chat))
                            }
                        }
                        when (state.phase) {
                            AssistantSessionPhase.MISSING_CONFIG -> MissingConfigContent(state, onAction)
                            AssistantSessionPhase.ERROR -> ErrorContent(state, onAction)
                            else -> SessionContent(state, onAction)
                        }
                    }
                    HorizontalDivider(color = panelContentColor.copy(alpha = 0.08f))
                }
                AssistantSearchBar(
                    state = state,
                    onAction = onAction,
                    contentColor = panelContentColor,
                )
            }
        }
    }
}

private fun AssistantOverlayUiState.shouldShowExpandedContent(): Boolean =
    phase == AssistantSessionPhase.MISSING_CONFIG ||
        phase == AssistantSessionPhase.ERROR ||
        pendingConfirmation != null ||
        userText.isNotBlank() ||
        responseText.isNotBlank() ||
        toolNames.isNotEmpty() ||
        ttsNotice ||
        canRetryListening

@Composable
private fun SessionContent(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
) {
    if (state.userText.isNotBlank()) {
        Text(
            text = state.userText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("assistant_user_text"),
        )
    }
    if (state.phase == AssistantSessionPhase.GENERATING && state.responseText.isBlank()) {
        StatusRow(stringResource(R.string.assistant_generating), true)
    }
    if (state.phase == AssistantSessionPhase.EXECUTING_TOOL) {
        StatusRow(
            stringResource(
                R.string.assistant_executing_tool,
                state.toolNames.lastOrNull().orEmpty(),
            ),
            true,
        )
    }
    if (state.responseText.isNotBlank()) {
        Text(
            text = state.responseText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("assistant_response_text"),
        )
    }
    state.pendingConfirmation?.let { pending ->
        ConfirmationCard(
            toolName = pending.toolName,
            arguments = pending.arguments,
            remainingSeconds = state.confirmationRemainingSeconds,
            onApprove = {
                onAction(
                    AssistantOverlayAction.ApproveConfirmation(
                        pending.confirmationCallId,
                    ),
                )
            },
            onReject = {
                onAction(
                    AssistantOverlayAction.RejectConfirmation(
                        pending.confirmationCallId,
                    ),
                )
            },
        )
    }
    if (state.phase == AssistantSessionPhase.SPEAKING || state.isSpeaking) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.assistant_speaking),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { onAction(AssistantOverlayAction.StopSpeaking) },
                modifier = Modifier.testTag("assistant_stop_speaking"),
            ) {
                Text(stringResource(R.string.assistant_stop_speaking))
            }
        }
    }
    if (state.ttsNotice) {
        Text(
            text = stringResource(R.string.assistant_tts_notice),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.66f),
        )
    }
    if (state.canRetryListening && state.phase != AssistantSessionPhase.LISTENING) {
        Text(
            text = stringResource(
                if (state.preparationFailed) {
                    R.string.assistant_preparation_failed
                } else {
                    R.string.assistant_no_speech
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.66f),
        )
    }
    if (state.phase == AssistantSessionPhase.FOLLOW_UP_IDLE && !state.canRetryListening) {
        Text(
            text = stringResource(R.string.assistant_follow_up_hint),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.66f),
        )
    }
}

@Composable
private fun MissingConfigContent(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
) {
    Text(
        text = stringResource(
            when (state.configIssue) {
                AssistantConfigIssue.MISSING_STT -> R.string.assistant_missing_stt
                else -> R.string.assistant_missing_model
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("assistant_missing_config"),
    )
}

@Composable
private fun ErrorContent(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
) {
    Column {
        Text(
            text = state.errorMessage
                ?: stringResource(R.string.assistant_transcribe_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("assistant_error"),
        )
        Button(
            onClick = { onAction(AssistantOverlayAction.RetryAfterError) },
            modifier = Modifier.testTag("assistant_retry"),
        ) {
            Text(stringResource(R.string.assistant_error_retry))
        }
    }
}

@Composable
private fun ConfirmationCard(
    toolName: String,
    arguments: Map<String, Any?>,
    remainingSeconds: Int,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    // 授权请求是中性的安全确认，不是错误：用 surfaceVariant 而非 errorContainer，
    // 红色只留给真正的失败状态。
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("assistant_confirmation"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.assistant_confirm_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.assistant_confirm_message, toolName),
                style = MaterialTheme.typography.bodyMedium,
            )
            confirmationArgumentsSummary(arguments).takeIf(String::isNotBlank)?.let { summary ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.assistant_confirm_arguments, summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.78f),
                    modifier = Modifier.testTag("assistant_confirm_arguments"),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.assistant_confirm_countdown, remainingSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.66f),
                modifier = Modifier.testTag("assistant_confirm_countdown"),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.testTag("assistant_confirm_approve"),
                ) {
                    Text(stringResource(R.string.assistant_confirm_approve))
                }
                TextButton(
                    onClick = onReject,
                    modifier = Modifier.testTag("assistant_confirm_reject"),
                ) {
                    Text(stringResource(R.string.assistant_confirm_reject))
                }
            }
        }
    }
}

internal fun confirmationArgumentsSummary(arguments: Map<String, Any?>): String =
    arguments.entries
        .joinToString(separator = ", ") { (key, rawValue) ->
            val value = rawValue?.toString().orEmpty()
            val shown = when {
                key.contains("phone", ignoreCase = true) ||
                    key.contains("number", ignoreCase = true) ->
                    value.takeLast(4).padStart(value.length.coerceAtMost(8), '•')
                key.contains("message", ignoreCase = true) ||
                    key.contains("text", ignoreCase = true) ->
                    value.take(40)
                else -> value.take(60)
            }
            "$key: $shown"
        }
        .take(160)

/** 底部输入行：容器与阴影由外层单面板提供，这里只负责排布。 */
@Composable
private fun AssistantSearchBar(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onAction(AssistantOverlayAction.CloseOverlay) },
            modifier = Modifier
                .size(48.dp)
                .testTag("assistant_close"),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.assistant_close),
                tint = contentColor.copy(alpha = 0.72f),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            SearchBarContent(
                state = state,
                onAction = onAction,
                contentColor = contentColor,
            )
        }
        SearchBarAction(state, onAction)
    }
}

@Composable
private fun SearchBarContent(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    // 面板展开时进行态只由面板内的 StatusRow 呈现，输入条回退为禁用输入框，
    // 避免同一状态在面板与输入条中重复出现。
    val expanded = state.shouldShowExpandedContent()
    when {
        state.phase == AssistantSessionPhase.LISTENING -> {
            VoiceWaveform(
                levels = state.recordingLevels,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .testTag("assistant_waveform"),
            )
        }
        state.phase == AssistantSessionPhase.PREPARING ||
            state.phase == AssistantSessionPhase.TRANSCRIBING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(
                        if (state.phase == AssistantSessionPhase.PREPARING) {
                            R.string.assistant_preparing
                        } else {
                            R.string.assistant_transcribing
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor.copy(alpha = 0.72f),
                )
            }
        }
        !expanded && (
            state.phase == AssistantSessionPhase.GENERATING ||
                state.phase == AssistantSessionPhase.EXECUTING_TOOL ||
                state.phase == AssistantSessionPhase.AWAITING_CONFIRMATION
            ) -> {
            Text(
                text = stringResource(
                    if (state.phase == AssistantSessionPhase.EXECUTING_TOOL) {
                        R.string.assistant_executing_tool
                    } else {
                        R.string.assistant_generating
                    },
                    *if (state.phase == AssistantSessionPhase.EXECUTING_TOOL) {
                        arrayOf(state.toolNames.lastOrNull().orEmpty())
                    } else {
                        emptyArray()
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.72f),
            )
        }
        else -> {
            BasicTextField(
                value = state.draftText,
                onValueChange = { onAction(AssistantOverlayAction.DraftChanged(it)) },
                enabled = state.inputEnabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (state.inputEnabled) {
                        contentColor
                    } else {
                        contentColor.copy(alpha = 0.38f)
                    },
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (state.draftText.isNotBlank()) {
                            onAction(AssistantOverlayAction.SubmitDraft)
                        }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assistant_input"),
                decorationBox = { innerTextField ->
                    if (state.draftText.isBlank()) {
                        Text(
                            text = stringResource(R.string.assistant_input_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor.copy(
                                alpha = if (state.inputEnabled) 0.62f else 0.38f,
                            ),
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

@Composable
private fun SearchBarAction(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
) {
    // 右侧始终是全页唯一的实心主按钮：听音=停止、空闲=麦克风、有草稿=发送。
    when {
        state.phase == AssistantSessionPhase.LISTENING -> {
            FilledIconButton(
                onClick = { onAction(AssistantOverlayAction.StopListening) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("assistant_stop_listening"),
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = stringResource(R.string.assistant_stop_task),
                )
            }
        }
        state.phase == AssistantSessionPhase.GENERATING ||
            state.phase == AssistantSessionPhase.EXECUTING_TOOL ||
            state.phase == AssistantSessionPhase.AWAITING_CONFIRMATION -> {
            FilledIconButton(
                onClick = { onAction(AssistantOverlayAction.StopTask) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("assistant_stop_task"),
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = stringResource(R.string.assistant_stop_task),
                )
            }
        }
        state.draftText.isNotBlank() -> {
            FilledIconButton(
                onClick = { onAction(AssistantOverlayAction.SubmitDraft) },
                enabled = state.inputEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("assistant_send"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.assistant_send),
                )
            }
        }
        else -> {
            FilledIconButton(
                onClick = { onAction(AssistantOverlayAction.MicTapped) },
                enabled = state.inputEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("assistant_mic"),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = stringResource(R.string.assistant_mic_cd),
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, loading: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .testTag("assistant_status_spinner"),
                strokeWidth = 2.dp,
            )
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 简易 RMS 电平柱条波形。 */
@Composable
private fun VoiceWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.assistant_waveform_cd)
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier.semantics { contentDescription = description },
    ) {
        val barCount = 32
        val spacing = size.width / barCount
        val barWidth = spacing * 0.6f
        val recent = levels.takeLast(barCount)
        for (index in 0 until barCount) {
            val level = recent.getOrNull(index - (barCount - recent.size)) ?: 0f
            val barHeight = (size.height * 0.15f) + (size.height * 0.85f * level.coerceIn(0f, 1f))
            val x = index * spacing + (spacing - barWidth) / 2f
            drawLine(
                color = barColor,
                start = Offset(x + barWidth / 2f, (size.height - barHeight) / 2f),
                end = Offset(x + barWidth / 2f, (size.height + barHeight) / 2f),
                strokeWidth = barWidth,
            )
        }
    }
}
