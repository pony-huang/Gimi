package github.ponyhuang.gimi.data.speech.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.speech.repository.SpeechSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 持久化语音播报偏好。`auto_speak_enabled` 键不存在时取默认开启——与产品预期一致：
 * 用户从未操作过开关时，每轮回复自动朗读。
 */
@Singleton
class SpeechSettingsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : SpeechSettingsRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _autoSpeakEnabled = MutableStateFlow(
        preferences.getBoolean(AUTO_SPEAK_ENABLED_KEY, DEFAULT_AUTO_SPEAK_ENABLED),
    )

    override val autoSpeakEnabled: StateFlow<Boolean> = _autoSpeakEnabled.asStateFlow()

    override fun setAutoSpeakEnabled(enabled: Boolean) {
        if (_autoSpeakEnabled.value == enabled) return
        _autoSpeakEnabled.value = enabled
        preferences.edit { putBoolean(AUTO_SPEAK_ENABLED_KEY, enabled) }
    }

    private companion object {
        const val PREFERENCES_NAME = "speech_preferences"
        const val AUTO_SPEAK_ENABLED_KEY = "auto_speak_enabled"
        const val DEFAULT_AUTO_SPEAK_ENABLED = true
    }
}
