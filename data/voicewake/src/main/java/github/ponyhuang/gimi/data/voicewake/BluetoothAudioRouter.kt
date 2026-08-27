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

data class BluetoothAudioRoute(
    val input: AudioDeviceInfo,
    val communication: AudioDeviceInfo,
    override val name: String,
) : VoiceAudioRoute {
    override val id: String get() = "bluetooth:${input.id}"
}

/** 手机外放路由：使用系统默认麦克风与扬声器，不绑定任何蓝牙设备。 */
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

    /**
     * 依据「仅蓝牙」开关选择音频路由：优先使用已连接的蓝牙 SCO 耳机；
     * 允许外放时，在无蓝牙耳机的情况下回退到手机外放。
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun findRoute(bluetoothOnly: Boolean): VoiceAudioRoute? {
        val bluetooth = runCatching { findBluetoothRoute() }.getOrNull()
        if (bluetooth != null) return bluetooth
        if (bluetoothOnly) return null
        return SpeakerAudioRoute(context.getString(R.string.bluetooth_voice_speaker_device_name))
    }

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
