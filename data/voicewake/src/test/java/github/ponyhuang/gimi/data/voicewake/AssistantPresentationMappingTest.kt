package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantPresentationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantPresentationMappingTest {
    @Test
    fun `capture transcribe agent and speech statuses map to presentation events`() {
        assertEquals(
            AssistantPresentationEvent.CaptureStarted(AssistantInvocationSource.BLUETOOTH_WAKE),
            VoicePipelineEvent(
                BluetoothVoiceStatus.CapturingCommand,
                "请说话",
                startsInteraction = true,
            ).assistantPresentationEvent(),
        )
        assertEquals(
            AssistantPresentationEvent.Transcribing,
            VoicePipelineEvent(BluetoothVoiceStatus.Transcribing, "识别中").assistantPresentationEvent(),
        )
        assertEquals(
            AssistantPresentationEvent.TranscriptReady("打开地图"),
            VoicePipelineEvent(
                BluetoothVoiceStatus.RunningAgent,
                "运行中",
                lastCommand = "打开地图",
            ).assistantPresentationEvent(),
        )
        assertEquals(
            AssistantPresentationEvent.Speaking,
            VoicePipelineEvent(BluetoothVoiceStatus.Speaking, "播报中").assistantPresentationEvent(),
        )
    }

    @Test
    fun `tool confirmation capture does not start a new presentation turn`() {
        assertNull(
            VoicePipelineEvent(
                BluetoothVoiceStatus.CapturingCommand,
                "请确认",
                startsInteraction = false,
            ).assistantPresentationEvent(),
        )
    }

    @Test
    fun `returning to listening after a command completes the presentation`() {
        assertEquals(
            AssistantPresentationEvent.Completed,
            VoicePipelineEvent(
                BluetoothVoiceStatus.Listening,
                "已经打开地图",
                lastCommand = "打开地图",
                completesInteraction = true,
            ).assistantPresentationEvent(),
        )
        assertNull(
            VoicePipelineEvent(BluetoothVoiceStatus.Listening, "等待唤醒").assistantPresentationEvent(),
        )
    }

    @Test
    fun `error maps its user facing message`() {
        assertEquals(
            AssistantPresentationEvent.Failed("网络失败"),
            VoicePipelineEvent(BluetoothVoiceStatus.Error, "网络失败").assistantPresentationEvent(),
        )
    }

    @Test
    fun `stopped interaction maps separately from failure`() {
        assertEquals(
            AssistantPresentationEvent.Stopped,
            VoicePipelineEvent(
                BluetoothVoiceStatus.Listening,
                "已停止",
                stopsInteraction = true,
            ).assistantPresentationEvent(),
        )
    }

    @Test
    fun `abandoned capture hides presentation instead of lingering`() {
        assertEquals(
            AssistantPresentationEvent.CaptureAbandoned,
            VoicePipelineEvent(
                BluetoothVoiceStatus.Listening,
                "未听到任务",
                abandonsInteraction = true,
            ).assistantPresentationEvent(),
        )
    }
}
