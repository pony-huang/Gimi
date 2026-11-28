package github.ponyhuang.gimi.domain.a2ui

import kotlinx.coroutines.flow.Flow

/** 设备实际媒体音量、有效档位范围及当前是否可修改。 */
data class A2uiMediaVolume(val level: Int, val minimum: Int, val maximum: Int, val adjustable: Boolean)

/** 手电筒的实时灯态及影响当前操作的条件。 */
enum class A2uiFlashlightState {
    Loading, Disabled, PermissionRequired, PermissionUnavailable, Unavailable, Off, On,
}

/** 聊天控件对设备的实时读写，由宿主授权并仅响应用户操作。 */
interface A2uiDeviceControls {
    fun observeVolume(): Flow<A2uiMediaVolume?>
    fun observeFlashlight(): Flow<A2uiFlashlightState>
    suspend fun setVolume(level: Int): Boolean
    suspend fun setFlashlight(enabled: Boolean): Boolean
}
