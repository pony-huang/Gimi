package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.data.agent.tools.system.AudioTool
import github.ponyhuang.gimi.data.agent.tools.system.FlashlightTool
import github.ponyhuang.gimi.domain.a2ui.A2uiDeviceControls
import github.ponyhuang.gimi.domain.a2ui.A2uiFlashlightState
import github.ponyhuang.gimi.domain.a2ui.A2uiMediaVolume
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 把已授权的设备操作复用到用户直接操作的聊天控件。 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidA2uiDeviceControls @Inject constructor(
    private val audio: AudioTool,
    private val flashlight: FlashlightTool,
    private val authorization: ToolAuthorizationRepository,
) : A2uiDeviceControls {
    private val volumeChanges = MutableStateFlow(0L)

    override fun observeVolume() =
        combine(authorization.revision, volumeChanges) { _, _ -> authorization.enabledToolIds() }
            .flatMapLatest { enabled ->
                if (GET_VOLUME !in enabled) flowOf<A2uiMediaVolume?>(null) else flow {
                    // 只在控件可见时采样，以同步系统音量键和 Agent 调用。
                    while (true) {
                        emit(audio.readVolumeForControl(SET_VOLUME in enabled))
                        delay(500)
                    }
                }
            }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun observeFlashlight() = authorization.revision.flatMapLatest {
        val enabled = authorization.enabledToolIds()
        when {
            SET_FLASHLIGHT !in enabled -> flowOf(A2uiFlashlightState.Disabled)
            !flashlight.hasCameraPermission() -> flowOf(
                if (REQUEST_FLASHLIGHT_PERMISSION in enabled) A2uiFlashlightState.PermissionRequired
                else A2uiFlashlightState.PermissionUnavailable,
            )
            else -> flashlight.observeTorch().map { on ->
                when (on) {
                    true -> A2uiFlashlightState.On
                    false -> A2uiFlashlightState.Off
                    null -> A2uiFlashlightState.Unavailable
                }
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override suspend fun setVolume(level: Int): Boolean = withContext(Dispatchers.IO) {
        val enabled = authorization.enabledToolIds()
        if (GET_VOLUME !in enabled || SET_VOLUME !in enabled) return@withContext false
        val confirmed = audio.setVolumeFromControl(level)
        volumeChanges.update { it + 1 }
        confirmed
    }

    override suspend fun setFlashlight(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (SET_FLASHLIGHT !in authorization.enabledToolIds()) return@withContext false
        if (flashlight.setFlashlight(enabled)["success"] != true) return@withContext false
        // 请求被接受不代表灯已亮。短暂等待 CameraManager 确认真正的灯态。
        withTimeoutOrNull(2_000) {
            flashlight.observeTorch().firstOrNull { it == enabled } != null
        } == true
    }

    private companion object {
        const val GET_VOLUME = "get_media_volume"
        const val SET_VOLUME = "set_media_volume"
        const val SET_FLASHLIGHT = "set_flashlight"
        const val REQUEST_FLASHLIGHT_PERMISSION = "request_flashlight_permission"
    }
}
