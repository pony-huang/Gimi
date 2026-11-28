package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.compose.runtime.compositionLocalOf
import github.ponyhuang.gimi.domain.a2ui.A2uiFlashlightState
import github.ponyhuang.gimi.domain.a2ui.A2uiMediaVolume
import kotlinx.coroutines.flow.StateFlow

/** 由聊天 Route 提供的设备控件状态和显式用户操作。 */
internal class ChatA2uiDeviceActions(
    val volume: StateFlow<A2uiMediaVolume?>,
    val flashlight: StateFlow<A2uiFlashlightState>,
    val changingVolume: Boolean,
    val changingFlashlight: Boolean,
    val permissionNeedsSettings: Boolean,
    val setVolume: (Int) -> Unit,
    val setFlashlight: (Boolean) -> Unit,
    val requestFlashlightPermission: () -> Unit,
)

internal val LocalA2uiDeviceActions = compositionLocalOf<ChatA2uiDeviceActions?> { null }
