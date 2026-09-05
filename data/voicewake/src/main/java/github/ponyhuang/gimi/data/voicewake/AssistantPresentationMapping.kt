package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantPresentationEvent

/** 将语音运行时事件转换为与具体 Android 界面无关的助手展示事件。 */
fun VoicePipelineEvent.assistantPresentationEvent(): AssistantPresentationEvent? = when (status) {
    BluetoothVoiceStatus.CapturingCommand -> AssistantPresentationEvent
        .CaptureStarted(AssistantInvocationSource.BLUETOOTH_WAKE)
        .takeIf { startsInteraction }
    BluetoothVoiceStatus.Transcribing -> AssistantPresentationEvent.Transcribing
    BluetoothVoiceStatus.RunningAgent -> lastCommand
        ?.takeIf(String::isNotBlank)
        ?.let(AssistantPresentationEvent::TranscriptReady)
    BluetoothVoiceStatus.Speaking -> AssistantPresentationEvent.Speaking
    BluetoothVoiceStatus.Error -> AssistantPresentationEvent.Failed(message)
    BluetoothVoiceStatus.Listening -> when {
        stopsInteraction -> AssistantPresentationEvent.Stopped
        completesInteraction -> AssistantPresentationEvent.Completed
        abandonsInteraction -> AssistantPresentationEvent.CaptureAbandoned
        else -> null
    }
    BluetoothVoiceStatus.Starting,
    BluetoothVoiceStatus.Paused,
    BluetoothVoiceStatus.Stopped,
    -> null
}
