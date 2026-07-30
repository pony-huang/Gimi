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

data class BluetoothAudioRoute(
    val input: AudioDeviceInfo,
    val communication: AudioDeviceInfo,
    val name: String,
)

@Singleton
class BluetoothAudioRouter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousMode: Int? = null
    private var callback: AudioDeviceCallback? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun findRoute(): BluetoothAudioRoute? {
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

    fun activate(route: BluetoothAudioRoute): Boolean {
        if (previousMode == null) previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        return audioManager.setCommunicationDevice(route.communication)
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
