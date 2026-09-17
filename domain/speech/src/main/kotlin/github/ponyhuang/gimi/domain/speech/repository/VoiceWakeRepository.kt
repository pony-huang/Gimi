package github.ponyhuang.gimi.domain.speech.repository

import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import kotlinx.coroutines.flow.StateFlow

/** 前台设备端语音唤醒的领域入口。 */
interface VoiceWakeRepository {
    val state: StateFlow<VoiceWakeState>

    fun setEnabled(enabled: Boolean)

    /** 由应用根 Activity 转发整个应用是否处于可见状态。 */
    fun setForeground(foreground: Boolean)

    /** 权限请求或系统设置返回后重新核对录音权限。 */
    fun refreshPermission()

    fun addTriggerPhrase(phrase: String): Result<Unit>

    fun removeTriggerPhrase(phrase: String): Result<Unit>
}
