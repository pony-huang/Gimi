package github.ponyhuang.gimi.domain.voice.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * 记住上次使用的语音对话模式。
 *
 * 模式在抽屉里被预选（或通话真正启动）时写入；下次进入语音页时作为预选模式恢复，
 * 让用户不必每次都重新选择。实现须容忍损坏数据：无法解析时回落 [RealtimeVoiceMode.Simple]。
 */
interface RealtimeVoiceModeRepository {
    val lastMode: StateFlow<RealtimeVoiceMode>

    fun setLastMode(mode: RealtimeVoiceMode)
}
