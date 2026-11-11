package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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

/** 通话中：右上角设备图标满墨色高亮，底栏左槽换成「更多选项」。 */
@Preview(name = "Device Header Light", widthDp = 390, heightDp = 844, locale = "zh-rCN")
@Preview(name = "Device Header Dark", widthDp = 390, heightDp = 844, locale = "zh-rCN", uiMode = 0x20)
@Composable
private fun VoiceDevicesPreview() {
    GimiTheme {
        RealtimeVoiceScreen(
            uiState = RealtimeVoiceUiState(voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening,
                listening = true, audioRouting = previewRouting), modelName = "GPT-5.4",
                ttsModelName = "mimo-v2-tts", sttModelName = "speech-2.8"),
            started = true, onStartCall = {}, onToggleMute = {}, onEnd = {},
        )
    }
}

/**
 * 设备下拉菜单的行。
 *
 * 菜单本体是 Popup，预览里渲染不出来，这里只把菜单项平铺出来看排版：选中对勾、
 * 「正在使用」标记与「自动」项的实际输出各自落在哪一行。真机上菜单展开态仍需手动看。
 */
@Composable
private fun VoiceDeviceMenuPreviewHost(routing: VoiceAudioRouting) {
    GimiTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                VoiceAudioDeviceMenuItems(routing) {}
            }
        }
    }
}

@Preview(name = "Menu Automatic Light", widthDp = 390, locale = "zh-rCN")
@Preview(name = "Menu Automatic Dark", widthDp = 390, locale = "zh-rCN", uiMode = 0x20)
@Composable
private fun VoiceDeviceMenuPreview() {
    VoiceDeviceMenuPreviewHost(previewRouting)
}

/** 手动选中某个设备：对勾移动，实际输出设备仍单独标注「正在使用」。 */
@Preview(name = "Menu Manual", widthDp = 390, locale = "zh-rCN")
@Composable
private fun VoiceDeviceManualPreview() {
    VoiceDeviceMenuPreviewHost(previewRouting.copy(preferredDeviceId = 1))
}

/** 切换未被平台确认：右上角图标位换成进度环。 */
@Preview(name = "Switching", widthDp = 390, heightDp = 844, locale = "zh-rCN")
@Composable
private fun VoiceDeviceSwitchingPreview() {
    GimiTheme {
        RealtimeVoiceScreen(
            uiState = RealtimeVoiceUiState(voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening,
                audioRouting = previewRouting.copy(preferredDeviceId = 3, switching = true)), modelName = "GPT-5.4"),
            started = true, onStartCall = {}, onToggleMute = {}, onEnd = {},
        )
    }
}
