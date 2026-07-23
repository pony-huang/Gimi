package github.ponyhuang.asssistantai.voice

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.domain.speech.model.WakeKeywordException
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelCatalog
import github.ponyhuang.asssistantai.domain.speech.model.validateWakeKeyword
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
    private val _keyword = MutableStateFlow(loadKeyword(_activeModelId.value))
    /** 激活的唤醒模型 id。 */
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    /** 激活模型语言下的唤醒词（切换模型时恢复该语言已保存的词或默认词）。 */
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    fun setActiveModel(modelId: String) {
        val info = requireNotNull(WakeModelCatalog.byId(modelId)) { "Unknown wake model: $modelId" }
        _activeModelId.value = info.id
        preferences.edit { putString(ACTIVE_MODEL_KEY, info.id) }
        _keyword.value = loadKeyword(info.id)
    }

    /** 设置激活模型语言的唤醒词，校验失败抛 [WakeKeywordException]。 */
    fun setKeyword(value: String) {
        val info = WakeModelCatalog.byId(_activeModelId.value) ?: WakeModelCatalog.default
        val normalized = value.trim()
        validateWakeKeyword(normalized, info.languageTag)?.let { throw WakeKeywordException(it) }
        _keyword.value = normalized
        preferences.edit { putString(keywordKey(info.languageTag), normalized) }
    }

    private fun loadKeyword(modelId: String): String {
        val info = WakeModelCatalog.byId(modelId) ?: WakeModelCatalog.default
        return preferences.getString(keywordKey(info.languageTag), null)
            ?.takeIf(String::isNotBlank)
            ?: info.defaultKeyword
    }

    private companion object {
        const val PREFERENCES_NAME = "bluetooth_voice_preferences"
        const val ACTIVE_MODEL_KEY = "active_model_id"
        const val KEYWORD_KEY_PREFIX = "wake_keyword."

        fun keywordKey(languageTag: String) = "$KEYWORD_KEY_PREFIX$languageTag"
    }
}
