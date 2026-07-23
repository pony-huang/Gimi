package github.ponyhuang.asssistantai.feature.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionPhase

/** 宽屏下的浮层内容最大宽度。 */
private val OverlayMaxWidth = 560.dp

/**
 * 无状态助理浮层：状态经 [state] 进入，事件经 [onAction] 离开。
 * 遮罩、安全边距与窗口属性由宿主 Activity/主题负责。
 */
@Composable
fun AssistantOverlayScreen(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
    onOpenInChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = OverlayMaxWidth)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OverlayHeader(state, onAction)
                Spacer(Modifier.height(8.dp))
                when (state.phase) {
                    AssistantSessionPhase.MISSING_CONFIG -> MissingConfigContent(state, onAction)
                    AssistantSessionPhase.ERROR -> ErrorContent(state, onAction)
                    else -> SessionContent(state, onAction)
                }
                Spacer(Modifier.height(8.dp))
                OverlayFooter(state, onAction, onOpenInChat)
            }
        }
    }
}

@Composable
private fun OverlayHeader(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.assistant_overlay_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
        if (state.phase == AssistantSessionPhase.GENERATING ||
            state.phase == AssistantSessionPhase.EXECUTING_TOOL ||
            state.phase == AssistantSessionPhase.AWAITING_CONFIRMATION
        ) {
            TextButton(
                onClick = { onAction(AssistantOverlayAction.StopTask) },
                modifier = Modifier.testTag("assistant_stop_task"),
            ) {
                Text(stringResource(R.string.assistant_stop_task))
            }
        }
        IconButton(
            onClick = { onAction(AssistantOverlayAction.CloseOverlay) },
            modifier = Modifier.testTag("assistant_close"),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.assistant_close),
            )
        }
    }
}

@Composable
private fun SessionContent(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
) {
    when (state.phase) {
        AssistantSessionPhase.PREPARING -> StatusRow(stringResource(R.string.assistant_preparing), true)
        AssistantSessionPhase.LISTENING -> ListeningContent(state, onAction)
        AssistantSessionPhase.TRANSCRIBING -> StatusRow(stringResource(R.string.assistant_transcribing), true)
        else -> Unit
    }

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
            remainingSeconds = state.confirmationRemainingSeconds,
            onApprove = { onAction(AssistantOverlayAction.ApproveConfirmation) },
            onReject = { onAction(AssistantOverlayAction.RejectConfirmation) },
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.canRetryListening && state.phase != AssistantSessionPhase.LISTENING) {
        Text(
            text = stringResource(R.string.assistant_no_speech),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.phase == AssistantSessionPhase.FOLLOW_UP_IDLE) {
        Text(
            text = stringResource(R.string.assistant_follow_up_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListeningContent(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VoiceWaveform(
            levels = state.recordingLevels,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("assistant_waveform"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.assistant_listening),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = { onAction(AssistantOverlayAction.StopListening) },
            modifier = Modifier.testTag("assistant_stop_listening"),
        ) {
            Text(stringResource(R.string.assistant_stop_task))
        }
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
            text = state.errorMessage.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("assistant_error"),
        )
        FilledTonalButton(
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
    remainingSeconds: Int,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("assistant_confirmation"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.assistant_confirm_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.assistant_confirm_message, toolName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.assistant_confirm_countdown, remainingSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
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
                FilledTonalButton(
                    onClick = onReject,
                    modifier = Modifier.testTag("assistant_confirm_reject"),
                ) {
                    Text(stringResource(R.string.assistant_confirm_reject))
                }
            }
        }
    }
}

@Composable
private fun OverlayFooter(
    state: AssistantOverlayUiState,
    onAction: (AssistantOverlayAction) -> Unit,
    onOpenInChat: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.draftText,
            onValueChange = { onAction(AssistantOverlayAction.DraftChanged(it)) },
            placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
            enabled = state.inputEnabled,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("assistant_input"),
        )
        IconButton(
            onClick = { onAction(AssistantOverlayAction.MicTapped) },
            enabled = state.inputEnabled,
            modifier = Modifier.testTag("assistant_mic"),
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = stringResource(R.string.assistant_mic_cd),
            )
        }
        IconButton(
            onClick = { onAction(AssistantOverlayAction.SubmitDraft) },
            enabled = state.inputEnabled && state.draftText.isNotBlank(),
            modifier = Modifier.testTag("assistant_send"),
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = stringResource(R.string.assistant_send),
            )
        }
    }
    TextButton(
        onClick = onOpenInChat,
        modifier = Modifier.testTag("assistant_open_in_chat"),
    ) {
        Text(stringResource(R.string.assistant_open_in_chat))
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
                    .height(18.dp)
                    .padding(end = 4.dp),
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
