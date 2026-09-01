package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal const val VOICE_CANCEL_TEST_TAG = "voice_recording_cancel"
internal const val VOICE_FINISH_TEST_TAG = "voice_recording_finish"
internal const val VOICE_WAVEFORM_TEST_TAG = "voice_recording_waveform"

@Composable
internal fun DefaultVoiceRecordingContent(params: VoiceRecordingContentParams) {
    val recordingState = if (params.externallyManaged) {
        stringResource(R.string.chat_voice_wake_capturing)
    } else {
        stringResource(R.string.chat_voice_recording_remaining, params.remainingSeconds)
    }
    val waveformDescription = stringResource(R.string.chat_voice_waveform)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp)
            .semantics { stateDescription = recordingState },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!params.externallyManaged) {
            FilledIconButton(
                onClick = params.onCancel,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(VOICE_CANCEL_TEST_TAG),
                colors = IconButtonDefaults.filledIconButtonColors(
                    // 与输入栏内其他按钮一致，比栏背景 surfaceContainer 抬一级
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.chat_voice_cancel_recording),
                )
            }
        }

        VoiceWaveform(
            levels = params.levels,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .testTag(VOICE_WAVEFORM_TEST_TAG)
                .semantics {
                    contentDescription = waveformDescription
                },
        )

        if (!params.externallyManaged) {
            FilledIconButton(
                onClick = params.onFinish,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(VOICE_FINISH_TEST_TAG),
            ) {
                // 语义是"完成录音并转写"，用对勾而非停止方块——方块容易被理解为"中断丢弃"。
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.chat_voice_finish_recording),
                )
            }
        }
    }
}

@Composable
private fun VoiceWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val spacing = 3.dp.toPx()
        val barCount = (size.width / (barWidth + spacing)).toInt().coerceAtLeast(1)
        val visibleLevels = levels.takeLast(barCount)
        val missingCount = barCount - visibleLevels.size
        val maxHeight = size.height * 0.88f

        repeat(barCount) { index ->
            val level = visibleLevels.getOrNull(index - missingCount)
            val barHeight = if (level == null) {
                barWidth
            } else {
                (barWidth + (maxHeight - barWidth) * level.coerceIn(0.04f, 1f))
                    .coerceAtMost(maxHeight)
            }
            val x = index * (barWidth + spacing)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.9f),
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
