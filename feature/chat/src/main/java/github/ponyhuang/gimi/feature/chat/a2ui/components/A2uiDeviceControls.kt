package github.ponyhuang.gimi.feature.chat.a2ui.components

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.a2ui.A2uiFlashlightState
import github.ponyhuang.gimi.domain.a2ui.A2uiMediaVolume
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.feature.chat.a2ui.LocalA2uiDeviceActions
import github.ponyhuang.gimi.ui.theme.GimiTheme
import kotlin.math.roundToInt

/** 固定媒体音量能力。目录只声明组件，实时值及写操作都由宿主提供。 */
internal object ChatMediaVolumeSlider : A2uiComponent {
    override val name = "MediaVolumeSlider"
    override val description = "Interactive device media-volume control. The host supplies the current volume and applies user changes. Do not provide a value."
    override val properties = emptyList<androidx.a2ui.compose.runtime.A2uiProperty<*>>()

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val actions = LocalA2uiDeviceActions.current
        val volume = actions?.volume?.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)?.value
        MediaVolumeControl(volume, actions?.changingVolume == true, actions?.setVolume ?: {}, modifier)
    }
}

/** 固定手电筒能力。历史文档中的属性无法触发写入或更改灯态。 */
internal object ChatFlashlightSwitch : A2uiComponent {
    override val name = "FlashlightSwitch"
    override val description = "Interactive device flashlight control. The host supplies the live torch state and permission prompt. Do not provide a value."
    override val properties = emptyList<androidx.a2ui.compose.runtime.A2uiProperty<*>>()

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val actions = LocalA2uiDeviceActions.current
        val state = actions?.flashlight?.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)?.value
        FlashlightControl(
            state = state ?: A2uiFlashlightState.Disabled,
            changing = actions?.changingFlashlight == true,
            onChange = actions?.setFlashlight ?: {},
            requestPermission = actions?.requestFlashlightPermission ?: {},
            openSettings = actions?.permissionNeedsSettings == true,
            modifier = modifier,
        )
    }
}

@Composable
private fun MediaVolumeControl(
    volume: A2uiMediaVolume?,
    changing: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        val label = stringResource(R.string.chat_a2ui_device_volume)
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (volume == null) {
            Text(stringResource(R.string.chat_a2ui_device_unavailable), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        var draft by remember(volume.minimum, volume.maximum) { mutableStateOf<Float?>(null) }
        val value = draft ?: volume.level.toFloat()
        val range = volume.maximum - volume.minimum
        val percent = if (range > 0) ((value - volume.minimum) * 100 / range).roundToInt() else 0
        Text(stringResource(R.string.chat_a2ui_device_percent, percent), style = MaterialTheme.typography.bodySmall)
        if (range > 0) {
            Slider(
                value = value,
                onValueChange = { draft = it },
                onValueChangeFinished = {
                    val target = draft?.roundToInt()
                    draft = null
                    if (target != null && target != volume.level) onChange(target)
                },
                enabled = volume.adjustable && !changing,
                valueRange = volume.minimum.toFloat()..volume.maximum.toFloat(),
                steps = range - 1,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun FlashlightControl(
    state: A2uiFlashlightState,
    changing: Boolean,
    onChange: (Boolean) -> Unit,
    requestPermission: () -> Unit,
    openSettings: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.chat_a2ui_device_flashlight)
    if (state == A2uiFlashlightState.On || state == A2uiFlashlightState.Off) {
        SettingSwitch(
            label = label,
            description = "",
            checked = state == A2uiFlashlightState.On,
            update = if (changing) null else { enabled -> enabled?.let(onChange) },
            modifier = modifier,
        )
        return
    }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(when (state) {
            A2uiFlashlightState.Loading -> R.string.chat_a2ui_device_reading
            A2uiFlashlightState.PermissionRequired, A2uiFlashlightState.PermissionUnavailable ->
                R.string.chat_a2ui_device_camera_required
            else -> R.string.chat_a2ui_device_unavailable
        }), style = MaterialTheme.typography.bodySmall)
        if (state == A2uiFlashlightState.PermissionRequired) {
            TextButton(onClick = requestPermission) {
                Text(stringResource(if (openSettings) R.string.chat_a2ui_device_camera_settings
                else R.string.chat_a2ui_device_camera_grant))
            }
        }
    }
}

@Preview
@Composable
private fun MediaVolumeControlPreview() {
    GimiTheme { MediaVolumeControl(A2uiMediaVolume(7, 0, 15, true), false, {}) }
}

@Preview
@Composable
private fun FlashlightControlPreview() {
    GimiTheme(darkTheme = true) { FlashlightControl(A2uiFlashlightState.On, false, {}, {}, false) }
}
