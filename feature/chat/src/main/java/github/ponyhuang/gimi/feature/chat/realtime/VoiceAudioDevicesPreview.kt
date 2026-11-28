package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDevice
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDeviceKind
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioRouting
import github.ponyhuang.gimi.ui.theme.GimiTheme

private val previewRouting = VoiceAudioRouting(
    devices = listOf(
        VoiceAudioDevice(1, VoiceAudioDeviceKind.Speaker, ""),
        VoiceAudioDevice(2, VoiceAudioDeviceKind.Bluetooth, "蓝牙耳机"),
        VoiceAudioDevice(3, VoiceAudioDeviceKind.Usb, "USB 耳机"),
    ),
    currentDeviceId = 2,
)

/** 通话中：底栏下方展示链路、模型与音色微胶囊组（不展示播放设备文案）。 */
@Preview(name = "Device Header Light", widthDp = 390, heightDp = 844, locale = "zh-rCN")
@Preview(name = "Device Header Dark", widthDp = 390, heightDp = 844, locale = "zh-rCN", uiMode = 0x20)
@Composable
private fun VoiceDevicesPreview() {
    GimiTheme {
        RealtimeVoiceScreen(
            uiState = RealtimeVoiceUiState(voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening,
                listening = true, audioRouting = previewRouting), modelName = "GPT-5.4",
                modelTemplateId = "openai",
                ttsModelName = "mimo-v2-tts", ttsModelTemplateId = "mimo",
                sttModelName = "speech-2.8", sttModelTemplateId = "minimax",
                voiceName = "童童"),
            started = true, onStartCall = {}, onToggleMute = {}, onEnd = {},
        )
    }
}

/** 切换未被平台确认：内容区显示切换指示。 */
@Preview(name = "Switching", widthDp = 390, heightDp = 844, locale = "zh-rCN")
@Composable
private fun VoiceDeviceSwitchingPreview() {
    GimiTheme {
        RealtimeVoiceScreen(
            uiState = RealtimeVoiceUiState(voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening,
                audioRouting = previewRouting.copy(switching = true)), modelName = "GPT-5.4"),
            started = true, onStartCall = {}, onToggleMute = {}, onEnd = {},
        )
    }
}
