package github.ponyhuang.asssistantai.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal const val VOICE_CANCEL_TEST_TAG = "voice_recording_cancel"
internal const val VOICE_FINISH_TEST_TAG = "voice_recording_finish"
internal const val VOICE_WAVEFORM_TEST_TAG = "voice_recording_waveform"

@Composable
internal fun DefaultVoiceRecordingContent(params: VoiceRecordingContentParams) {
    val recordingState = stringResource(
        R.string.chat_voice_recording_remaining,
        params.remainingSeconds,
    )
    val waveformDescription = stringResource(R.string.chat_voice_waveform)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 2.dp)
            .semantics { stateDescription = recordingState },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = params.onCancel,
            modifier = Modifier.testTag(VOICE_CANCEL_TEST_TAG),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.chat_voice_cancel_recording),
            )
        }

        Text(
            text = formatRecordingTime(params.remainingSeconds),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )

        VoiceWaveform(
            levels = params.levels,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 32.dp)
                .testTag(VOICE_WAVEFORM_TEST_TAG)
                .semantics {
                    contentDescription = waveformDescription
                },
        )

        FilledIconButton(
            onClick = params.onFinish,
            modifier = Modifier.testTag(VOICE_FINISH_TEST_TAG),
        ) {
            Icon(
                painter = painterResource(R.drawable.stream_ai_compose_ic_stop),
                contentDescription = stringResource(R.string.chat_voice_finish_recording),
            )
        }
    }
}

@Composable
private fun VoiceWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val spacing = 2.dp.toPx()
        val barCount = (size.width / (barWidth + spacing)).toInt().coerceAtLeast(1)
        val visibleLevels = levels.takeLast(barCount)
        val missingCount = barCount - visibleLevels.size
        val minHeight = 4.dp.toPx()
        val maxHeight = size.height * 0.82f

        repeat(barCount) { index ->
            val level = if (index < missingCount) {
                BASELINE_LEVELS[index % BASELINE_LEVELS.size]
            } else {
                visibleLevels[index - missingCount].coerceIn(0f, 1f)
            }
            val barHeight = minHeight + ((maxHeight - minHeight) * level.coerceAtLeast(0.06f))
            val x = index * (barWidth + spacing)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.78f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

internal fun formatRecordingTime(remainingSeconds: Int): String {
    val safeSeconds = remainingSeconds.coerceAtLeast(0)
    return "%d:%02d".format(safeSeconds / 60, safeSeconds % 60)
}

private val BASELINE_LEVELS = listOf(0.08f, 0.16f, 0.11f, 0.22f, 0.13f, 0.18f)

@Preview(name = "Phone recording", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable recording", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet recording", device = Devices.TABLET, showBackground = true)
@Composable
private fun VoiceRecordingContentPreview() {
    MaterialTheme {
        DefaultVoiceRecordingContent(
            VoiceRecordingContentParams(
                levels = List(36) { index -> ((index % 7) + 1) / 8f },
                remainingSeconds = 47,
                onCancel = { },
                onFinish = { },
            ),
        )
    }
}
