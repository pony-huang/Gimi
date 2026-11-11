package github.ponyhuang.gimi.data.voice.repository

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDevice
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDeviceKind
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioRouting
import java.util.concurrent.Executor

/** 会话拥有的 Android 通话路由；所有访问和回调均在主线程，关闭后停止设备监听。 */
internal class VoiceAudioRoutingController(
    private val audio: AudioManager,
    private val onState: (VoiceAudioRouting) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var active = false
    private var snapshot = VoiceAudioRouting()
    private val connectionOrder = mutableListOf<Int>()
    private var requestedId: Int? = null
    private var previousDevice: AudioDeviceInfo? = null
    private val timeout = Runnable {
        if (active && requestedId != audio.communicationDevice?.id) {
            requestedId = null
            snapshot = snapshot.copy(preferredDeviceId = null, switching = false,
                noticeRevision = snapshot.noticeRevision + 1, selectionFailed = true)
            audio.clearCommunicationDevice()
            publish()
        }
    }
    private val refresh = Runnable { refreshDevices() }
    private val devicesCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = devicesChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = devicesChanged()
    }
    private val communicationListener = AudioManager.OnCommunicationDeviceChangedListener {
        if (active) {
            if (it?.id == requestedId) handler.removeCallbacks(timeout)
            publish()
        }
    }

    fun start() {
        previousDevice = audio.communicationDevice
        active = true
        audio.registerAudioDeviceCallback(devicesCallback, handler)
        audio.addOnCommunicationDeviceChangedListener(Executor { handler.post(it) }, communicationListener)
        refreshDevices()
    }

    fun select(deviceId: Int?) {
        if (!active) return
        if (deviceId != null && snapshot.devices.none { it.id == deviceId }) {
            snapshot = snapshot.copy(noticeRevision = snapshot.noticeRevision + 1, selectionFailed = true)
            publish()
            return
        }
        snapshot = snapshot.copy(preferredDeviceId = deviceId)
        requestedId = null
        refreshDevices()
    }

    private fun devicesChanged() {
        if (!active) return
        refreshDevices()
        // 蓝牙通话端点可能晚于物理设备通知出现，再读取一次平台可选列表。
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 1000)
    }

    private fun refreshDevices() {
        if (!active) return
        val available = audio.availableCommunicationDevices
        val ids = available.map { it.id }.toSet()
        val disconnected = snapshot.devices.any { it.id !in ids && it.kind in setOf(
            VoiceAudioDeviceKind.Bluetooth, VoiceAudioDeviceKind.Wired, VoiceAudioDeviceKind.Usb,
        ) && (it.id == snapshot.currentDeviceId || it.id == requestedId || it.id == snapshot.preferredDeviceId) }
        connectionOrder.retainAll(ids)
        available.forEach { if (it.id !in connectionOrder) connectionOrder.add(it.id) }
        snapshot = snapshot.copy(
            devices = available.map { VoiceAudioDevice(it.id, kind(it.type), it.productName.toString()) },
            preferredDeviceId = snapshot.preferredDeviceId?.takeIf { it in ids },
            noticeRevision = snapshot.noticeRevision + if (disconnected) 1 else 0,
            selectionFailed = if (disconnected) false else snapshot.selectionFailed,
        )
        val target = selectVoiceAudioDevice(snapshot.devices, connectionOrder, snapshot.preferredDeviceId)
        if (target != null && requestedId != target.id) {
            handler.removeCallbacks(timeout)
            requestedId = target.id
            run {
                val device = available.first { it.id == target.id }
                // 设备可能在读取与提交之间断开；这属于可恢复的路由失败。
                val accepted = try { audio.setCommunicationDevice(device) } catch (_: IllegalArgumentException) { false }
                if (accepted) handler.postDelayed(timeout, 30_000) else {
                    requestedId = null
                    snapshot = snapshot.copy(preferredDeviceId = null,
                        noticeRevision = snapshot.noticeRevision + 1, selectionFailed = true)
                }
            }
        }
        publish()
    }

    private fun publish() {
        snapshot = snapshot.copy(currentDeviceId = audio.communicationDevice?.id,
            switching = requestedId != null && requestedId != audio.communicationDevice?.id)
        onState(snapshot)
    }

    fun close() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        audio.unregisterAudioDeviceCallback(devicesCallback)
        audio.removeOnCommunicationDeviceChangedListener(communicationListener)
        audio.clearCommunicationDevice()
        previousDevice?.let { previous ->
            audio.availableCommunicationDevices.firstOrNull { it.id == previous.id }?.let(audio::setCommunicationDevice)
        }
        requestedId = null
        connectionOrder.clear()
    }

    private fun kind(type: Int): VoiceAudioDeviceKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> VoiceAudioDeviceKind.Speaker
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> VoiceAudioDeviceKind.Earpiece
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID -> VoiceAudioDeviceKind.Bluetooth
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> VoiceAudioDeviceKind.Wired
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> VoiceAudioDeviceKind.Usb
        else -> VoiceAudioDeviceKind.Other
    }
}
