package github.ponyhuang.asssistantai.data.assistant

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 共享语音会话 id 的读写契约（供测试替换实现）。 */
interface VoiceSessionIdStore {
    val voiceSessionId: StateFlow<String?>

    fun setVoiceSessionId(value: String?)
}

/**
 * 共享语音会话 id 的唯一所有者。
 *
 * 继续读写历史文件 `bluetooth_voice_preferences` 的 `voice_session_id` key，
 * 保证升级前保存的语音会话被直接复用、不丢失历史。
 */
@Singleton
class AssistantVoiceSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) : VoiceSessionIdStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _voiceSessionId = MutableStateFlow(preferences.getString(SESSION_ID_KEY, null))

    override val voiceSessionId: StateFlow<String?> = _voiceSessionId.asStateFlow()

    override fun setVoiceSessionId(value: String?) {
        _voiceSessionId.value = value
        preferences.edit {
            if (value.isNullOrBlank()) remove(SESSION_ID_KEY) else putString(SESSION_ID_KEY, value)
        }
    }

    companion object {
        const val PREFERENCES_NAME = "bluetooth_voice_preferences"
        const val SESSION_ID_KEY = "voice_session_id"
    }
}
