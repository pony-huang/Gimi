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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlin.math.abs
import kotlin.math.exp

internal const val VOICE_CANCEL_TEST_TAG = "voice_recording_cancel"
internal const val VOICE_FINISH_TEST_TAG = "voice_recording_finish"
internal const val VOICE_SEND_TEST_TAG = "voice_recording_send"
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
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp)
            .semantics { stateDescription = recordingState },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = params.onCancel,
            modifier = Modifier
                .size(48.dp)
                .testTag(VOICE_CANCEL_TEST_TAG),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.chat_voice_cancel_recording),
            )
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

        FilledIconButton(
            onClick = params.onFinish,
            modifier = Modifier
                .size(48.dp)
                .testTag(VOICE_FINISH_TEST_TAG),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.stream_ai_compose_ic_stop),
                modifier = Modifier.size(20.dp),
                contentDescription = stringResource(R.string.chat_voice_finish_recording),
            )
        }

        FilledIconButton(
            onClick = params.onSend,
            modifier = Modifier.size(48.dp).testTag(VOICE_SEND_TEST_TAG),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.stream_ai_compose_ic_send),
                contentDescription = stringResource(R.string.chat_voice_transcribe_and_send),
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
    var widthPx by remember { mutableStateOf(0) }
    var displayed by remember { mutableStateOf(emptyList<Float>()) }
    val stridePx = with(androidx.compose.ui.platform.LocalDensity.current) { 5.dp.toPx() }
    val barCount = (widthPx / stridePx).toInt().coerceAtLeast(1)
    val inspectionMode = LocalInspectionMode.current

    LaunchedEffect(levels, barCount) {
        val visible = levels.takeLast(barCount)
        val missingCount = barCount - visible.size
        val aligned = List(barCount) { index ->
            visible.getOrNull(index - missingCount)?.coerceIn(0f, 1f) ?: 0f
        }
        // 邻域平滑消除单根尖峰；没有输入时只显示静音圆点，不播放装饰性循环动画。
        val target = aligned.mapIndexed { index, level ->
            (aligned.getOrElse(index - 1) { level } + 2f * level + aligned.getOrElse(index + 1) { level }) / 4f
        }
        if (inspectionMode) {
            displayed = target
            return@LaunchedEffect
        }
        if (displayed.size != barCount) displayed = List(barCount) { 0f }
        var lastFrame = 0L
        do {
            val frame = withFrameNanos { it }
            // 新采样到达也立即绘制第一帧，不能每次重启都额外跳过一帧。
            val elapsedMs = if (lastFrame == 0L) 16f else ((frame - lastFrame) / 1_000_000f).coerceAtMost(64f)
            lastFrame = frame
            // 起音响应较快、收音回落较缓；逐帧逼近真实音量，采样更新时从当前显示值继续。
            displayed = displayed.mapIndexed { index, current ->
                val destination = target[index]
                val timeConstantMs = if (destination > current) 55f else 160f
                val next = current + (destination - current) * (1f - exp(-elapsedMs / timeConstantMs))
                if (abs(next - destination) < 0.002f) destination else next
            }
        } while (displayed.indices.any { abs(displayed[it] - target[it]) >= 0.002f })
    }

    Canvas(modifier = modifier.onSizeChanged { widthPx = it.width }) {
        val barWidth = 2.5.dp.toPx()
        val maxHeight = size.height * 0.72f

        repeat(barCount) { index ->
            val level = displayed.getOrElse(index) { 0f }
            val barHeight = barWidth + (maxHeight - barWidth) * level
            val x = index * stridePx
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.3f + 0.5f * level),
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
@Preview(name = "Phone dark recording", device = Devices.PHONE, showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Foldable recording", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet recording", device = Devices.TABLET, showBackground = true)
@Composable
private fun VoiceRecordingContentPreview() {
    AsssistantaiTheme {
        DefaultVoiceRecordingContent(
            VoiceRecordingContentParams(
                levels = List(36) { index -> ((index % 7) + 1) / 8f },
                remainingSeconds = 47,
                onCancel = { },
                onFinish = { },
                onSend = { },
            ),
        )
    }
}
