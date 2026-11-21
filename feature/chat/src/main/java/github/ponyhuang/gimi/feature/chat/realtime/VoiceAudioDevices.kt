package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDevice
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDeviceKind
import github.ponyhuang.gimi.feature.chat.R

/** 通话设备固定自动路由，界面只读：把实际输出设备翻译成可展示名称。 */
@Composable
internal fun voiceAudioDeviceName(device: VoiceAudioDevice?): String = when (device?.kind) {
    VoiceAudioDeviceKind.Speaker -> stringResource(R.string.voice_device_speaker)
    VoiceAudioDeviceKind.Earpiece -> stringResource(R.string.voice_device_earpiece)
    null -> stringResource(R.string.voice_device_unknown)
    else -> device.name.ifBlank {
        stringResource(when (device.kind) {
            VoiceAudioDeviceKind.Bluetooth -> R.string.voice_device_bluetooth
            VoiceAudioDeviceKind.Wired -> R.string.voice_device_wired
            VoiceAudioDeviceKind.Usb -> R.string.voice_device_usb
            else -> R.string.voice_device_other
        })
    }
}
