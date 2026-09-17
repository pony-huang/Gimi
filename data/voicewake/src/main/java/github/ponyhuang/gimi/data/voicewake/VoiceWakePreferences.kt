package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.speech.model.DEFAULT_WAKE_PHRASE
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 持久化用户的语音唤醒启用意图和有序短语列表。 */
@Singleton
class VoiceWakePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean(ENABLED_KEY, false))
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    private val mutableTriggerPhrases = MutableStateFlow(loadTriggerPhrases())
    val triggerPhrases: StateFlow<List<String>> = mutableTriggerPhrases.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean(ENABLED_KEY, enabled).commit()) {
            "Unable to persist voice wake enabled state"
        }
        mutableEnabled.value = enabled
    }

    fun setTriggerPhrases(phrases: List<String>) {
        require(phrases.isNotEmpty()) { "At least one wake phrase is required" }
        check(preferences.edit().putString(PHRASES_KEY, phrases.joinToString(SEPARATOR)).commit()) {
            "Unable to persist voice wake phrases"
        }
        mutableTriggerPhrases.value = phrases
    }

    private fun loadTriggerPhrases(): List<String> =
        preferences.getString(PHRASES_KEY, null)
            ?.split(SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.takeIf(List<String>::isNotEmpty)
            ?: listOf(DEFAULT_WAKE_PHRASE)

    private companion object {
        const val PREFERENCES_NAME = "voice_wake_preferences"
        const val ENABLED_KEY = "enabled"
        const val PHRASES_KEY = "trigger_phrases"
        const val SEPARATOR = "\u001F"
    }
}
