package github.ponyhuang.asssistantai.voice

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.domain.speech.model.isPresetWakeKeyword
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class BluetoothVoicePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _keyword = MutableStateFlow(
        preferences.getString(KEYWORD_KEY, null)?.takeIf(::isPresetWakeKeyword) ?: DEFAULT_WAKE_KEYWORD,
    )
    private val _voiceSessionId = MutableStateFlow(preferences.getString(SESSION_ID_KEY, null))

    val keyword: StateFlow<String> = _keyword.asStateFlow()
    val voiceSessionId: StateFlow<String?> = _voiceSessionId.asStateFlow()

    fun setKeyword(value: String) {
        val normalized = value.trim()
        require(isPresetWakeKeyword(normalized)) { "仅支持预置唤醒词" }
        _keyword.value = normalized
        preferences.edit { putString(KEYWORD_KEY, normalized) }
    }

    fun setVoiceSessionId(value: String?) {
        _voiceSessionId.value = value
        preferences.edit {
            if (value.isNullOrBlank()) remove(SESSION_ID_KEY) else putString(SESSION_ID_KEY, value)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "bluetooth_voice_preferences"
        const val KEYWORD_KEY = "wake_keyword"
        const val SESSION_ID_KEY = "voice_session_id"
    }
}
