package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDevice
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioDeviceKind
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioRouting
import github.ponyhuang.gimi.feature.chat.R

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

/**
 * 通话设备下拉菜单项。
 *
 * 系统确认的输出与用户偏好分别展示：对勾落在用户手动选中的那一项，「正在使用」只标在
 * 当前输出设备上，「自动」项把实际输出写在行内。菜单只有一行高度，状态一律用行内小字
 * 承载，不再像抽屉那样拆成两行副标题。
 */
@Composable
internal fun VoiceAudioDeviceMenuItems(routing: VoiceAudioRouting, onSelect: (Int?) -> Unit) {
    val current = routing.devices.firstOrNull { it.id == routing.currentDeviceId }
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.voice_device_auto))
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.voice_device_current, voiceAudioDeviceName(current)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = { onSelect(null) },
        leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(20.dp)) },
        trailingIcon = { if (routing.preferredDeviceId == null) VoiceDeviceSelectedMark() },
    )
    routing.devices.forEach { device ->
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(voiceAudioDeviceName(device))
                    if (device.id == routing.currentDeviceId) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.voice_device_in_use),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            onClick = { onSelect(device.id) },
            leadingIcon = { Icon(voiceAudioDeviceIcon(device), null, Modifier.size(20.dp)) },
            trailingIcon = { if (routing.preferredDeviceId == device.id) VoiceDeviceSelectedMark() },
        )
    }
}

/** 菜单尾部的选中标记：沿用抽屉选项行的蓝色对勾。 */
@Composable
private fun VoiceDeviceSelectedMark() {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = stringResource(R.string.voice_mode_selected),
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** 设备类型对应的语义图标：右上角触发按钮与菜单行共用同一套映射。 */
internal fun voiceAudioDeviceIcon(device: VoiceAudioDevice?): ImageVector = when (device?.kind) {
    VoiceAudioDeviceKind.Bluetooth -> Icons.Default.BluetoothAudio
    VoiceAudioDeviceKind.Wired, VoiceAudioDeviceKind.Usb -> Icons.Default.Headphones
    VoiceAudioDeviceKind.Earpiece -> Icons.Default.PhoneInTalk
    VoiceAudioDeviceKind.Speaker -> Icons.AutoMirrored.Filled.VolumeUp
    VoiceAudioDeviceKind.Other -> Icons.Default.Devices
    null -> Icons.AutoMirrored.Filled.VolumeUp
}
