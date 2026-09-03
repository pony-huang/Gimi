package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.model.WakeKeywordException
import github.ponyhuang.gimi.domain.speech.model.normalizeWakeKeyword
import github.ponyhuang.gimi.domain.speech.model.validateWakeKeyword
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
    private val _activeModelId = MutableStateFlow(
        preferences.getString(ACTIVE_MODEL_KEY, null)
            ?.let { WakeModelCatalog.byId(it)?.id }
            ?: WakeModelCatalog.default.id,
    )
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()
    private val _wakeWord = MutableStateFlow(loadWakeWord(_activeModelId.value))
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    fun setActiveModel(modelId: String) {
        val info = requireNotNull(WakeModelCatalog.byId(modelId)) { "Unknown wake model: $modelId" }
        _activeModelId.value = info.id
        preferences.edit { putString(ACTIVE_MODEL_KEY, info.id) }
        _wakeWord.value = loadWakeWord(info.id)
    }

    fun setWakeWord(modelId: String, value: String) {
        val model = requireNotNull(WakeModelCatalog.byId(modelId)) { "Unknown wake model: $modelId" }
        validateWakeKeyword(value, model)?.let { throw WakeKeywordException(it) }
        val normalized = normalizeWakeKeyword(value)
        check(preferences.edit().putString(wakeWordKey(model.id), normalized).commit()) {
            "Unable to persist wake word for ${model.id}"
        }
        if (_activeModelId.value == model.id) _wakeWord.value = normalized
    }

    private fun loadWakeWord(modelId: String): String {
        val model = WakeModelCatalog.byId(modelId) ?: WakeModelCatalog.default
        return preferences.getString(wakeWordKey(model.id), null)
            ?.let(::normalizeWakeKeyword)
            ?.takeIf(String::isNotBlank)
            ?: model.defaultWakeWord
    }

    fun wakeWord(modelId: String): String = loadWakeWord(modelId)

    private companion object {
        const val PREFERENCES_NAME = "bluetooth_voice_preferences"
        const val ACTIVE_MODEL_KEY = "active_model_id"
        const val WAKE_WORD_KEY_PREFIX = "wake_word."

        fun wakeWordKey(modelId: String) = "$WAKE_WORD_KEY_PREFIX$modelId"
    }
}
