package github.ponyhuang.gimi.data.voicewake

import android.Manifest
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.voicewake.R
import javax.inject.Inject
import javax.inject.Singleton

/** 语音唤醒的一条音频输入/输出路由，可能是蓝牙 SCO 或手机外放。 */
sealed interface VoiceAudioRoute {
    val name: String

    /** 用于判断路由是否变化的稳定标识。 */
    val id: String
}

/**
 * 可用于语音采集与播放的蓝牙 SCO 路由。
 *
 * @property input 录音器绑定的蓝牙输入设备。
 * @property communication 系统通信音频绑定的蓝牙设备。
 * @property name 面向用户展示的设备名称。
 */
data class BluetoothAudioRoute(
    val input: AudioDeviceInfo,
    val communication: AudioDeviceInfo,
    override val name: String,
) : VoiceAudioRoute {
    override val id: String get() = "bluetooth:${input.id}"
}

/**
 * 手机外放路由：使用系统默认麦克风与扬声器，不绑定任何蓝牙设备。
 *
 * @property name 面向用户展示的本机路由名称。
 */
data class SpeakerAudioRoute(
    override val name: String,
) : VoiceAudioRoute {
    override val id: String get() = "speaker"
}

@Singleton
class BluetoothAudioRouter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousMode: Int? = null
    private var callback: AudioDeviceCallback? = null

    /** 优先返回蓝牙 SCO 路由；不可用或无权限时回退到手机。 */
    fun findRoute(): VoiceAudioRoute {
        val bluetooth = runCatching { findBluetoothRoute() }.getOrNull()
        if (bluetooth != null) return bluetooth
        return SpeakerAudioRoute(context.getString(R.string.bluetooth_voice_speaker_device_name))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun findBluetoothRoute(): BluetoothAudioRoute? {
        val input = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: return null
        val communication = audioManager.availableCommunicationDevices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: return null
        val name = input.productName?.toString()?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.bluetooth_voice_default_device_name)
        return BluetoothAudioRoute(input, communication, name)
    }

    fun activate(route: VoiceAudioRoute): Boolean = when (route) {
        is BluetoothAudioRoute -> {
            if (previousMode == null) previousMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.setCommunicationDevice(route.communication)
        }
        is SpeakerAudioRoute -> {
            // 外放模式不进入通话模式，避免声音被路由到听筒。
            audioManager.clearCommunicationDevice()
            previousMode?.let { audioManager.mode = it }
            previousMode = null
            true
        }
    }

    /** 蓝牙路由激活失败时继续使用手机，不让监听停在等待蓝牙状态。 */
    fun activateWithFallback(route: VoiceAudioRoute): VoiceAudioRoute? {
        val speaker = SpeakerAudioRoute(context.getString(R.string.bluetooth_voice_speaker_device_name))
        return activatePreferredVoiceRoute(route, speaker, ::activate)
    }

    fun release() {
        audioManager.clearCommunicationDevice()
        previousMode?.let { audioManager.mode = it }
        previousMode = null
    }

    fun observe(onChanged: () -> Unit) {
        stopObserving()
        callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onChanged()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onChanged()
        }.also { audioManager.registerAudioDeviceCallback(it, null) }
    }

    fun stopObserving() {
        callback?.let(audioManager::unregisterAudioDeviceCallback)
        callback = null
    }
}

/** 激活首选路由，仅在蓝牙路由失败时尝试手机回退。 */
internal fun activatePreferredVoiceRoute(
    preferred: VoiceAudioRoute,
    speaker: SpeakerAudioRoute,
    activate: (VoiceAudioRoute) -> Boolean,
): VoiceAudioRoute? {
    if (runCatching { activate(preferred) }.getOrDefault(false)) return preferred
    if (preferred !is BluetoothAudioRoute) return null
    return speaker.takeIf { runCatching { activate(it) }.getOrDefault(false) }
}
