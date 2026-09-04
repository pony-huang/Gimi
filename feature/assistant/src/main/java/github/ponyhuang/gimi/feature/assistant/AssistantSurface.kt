package github.ponyhuang.gimi.feature.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState

/** 助手卡片在不同宿主中的密度规格。 */
enum class AssistantSurfaceMode {
    SHEET,
    OVERLAY,
    LOCK_SCREEN,
}

/** ChatGPT 风格的轻量助手状态卡片，业务状态和操作全部由宿主传入。 */
@Composable
fun AssistantSurface(
    state: AssistantSessionState,
    mode: AssistantSurfaceMode,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = mode == AssistantSurfaceMode.OVERLAY
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (compact) 560.dp else 640.dp),
        shape = RoundedCornerShape(if (compact) 28.dp else 32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 8.dp else 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = state.phase,
                    label = "assistant-phase",
                    modifier = Modifier.weight(1f),
                ) { phase ->
                    Text(
                        text = stringResource(phase.statusTextRes()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.assistant_action_close),
                    )
                }
            }

            if (state.phase.isAnimatedPhase()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            state.turn?.userText?.takeIf(String::isNotBlank)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val response = state.turn?.responseText?.takeIf(String::isNotBlank) ?: state.errorMessage
            response?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.taskActive) {
                    OutlinedButton(onClick = onStop) {
                        Icon(Icons.Rounded.StopCircle, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.assistant_action_stop))
                    }
                }
                Spacer(Modifier.size(8.dp))
                FilledTonalButton(onClick = onOpenChat) {
                    Text(stringResource(R.string.assistant_action_open_chat))
                }
            }
        }
    }
}

private fun AssistantSessionPhase.statusTextRes(): Int = when (this) {
    AssistantSessionPhase.PREPARING -> R.string.assistant_status_preparing
    AssistantSessionPhase.MISSING_CONFIG -> R.string.assistant_status_missing_config
    AssistantSessionPhase.BUSY -> R.string.assistant_status_busy
    AssistantSessionPhase.LISTENING -> R.string.assistant_status_listening
    AssistantSessionPhase.TRANSCRIBING -> R.string.assistant_status_transcribing
    AssistantSessionPhase.GENERATING -> R.string.assistant_status_generating
    AssistantSessionPhase.EXECUTING_TOOL -> R.string.assistant_status_executing_tool
    AssistantSessionPhase.AWAITING_CONFIRMATION -> R.string.assistant_status_confirming
    AssistantSessionPhase.SPEAKING -> R.string.assistant_status_speaking
    AssistantSessionPhase.FOLLOW_UP_IDLE -> R.string.assistant_status_completed
    AssistantSessionPhase.STOPPED -> R.string.assistant_status_stopped
    AssistantSessionPhase.ERROR -> R.string.assistant_status_error
}

private fun AssistantSessionPhase.isAnimatedPhase(): Boolean = when (this) {
    AssistantSessionPhase.PREPARING,
    AssistantSessionPhase.LISTENING,
    AssistantSessionPhase.TRANSCRIBING,
    AssistantSessionPhase.GENERATING,
    AssistantSessionPhase.EXECUTING_TOOL,
    AssistantSessionPhase.SPEAKING,
    -> true
    else -> false
}
