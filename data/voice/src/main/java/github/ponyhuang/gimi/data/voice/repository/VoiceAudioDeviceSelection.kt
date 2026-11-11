package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDevice
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDeviceKind

/** 手动选择优先；自动模式按连接顺序选耳机，最后回退到扬声器。 */
internal fun selectVoiceAudioDevice(
    devices: List<VoiceAudioDevice>,
    connectionOrder: List<Int>,
    preferredId: Int?,
): VoiceAudioDevice? = devices.firstOrNull { it.id == preferredId }
    ?: connectionOrder.asReversed().firstNotNullOfOrNull { id ->
        devices.firstOrNull { it.id == id && it.kind in headsetKinds }
    }
    ?: devices.firstOrNull { it.kind == VoiceAudioDeviceKind.Speaker }
    ?: devices.firstOrNull()

private val headsetKinds = setOf(VoiceAudioDeviceKind.Bluetooth, VoiceAudioDeviceKind.Wired, VoiceAudioDeviceKind.Usb)
