package github.ponyhuang.gimi.domain.assistant.repository

import kotlinx.coroutines.flow.StateFlow

/** 共享语音会话 id 的读写契约（供测试替换实现）。 */
interface VoiceSessionStore {
    val voiceSessionId: StateFlow<String?>

    fun setVoiceSessionId(value: String?)
}
