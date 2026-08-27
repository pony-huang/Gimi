package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
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

    /** 是否仅在蓝牙耳机（SCO）连接时才监听；默认 true 保持历史行为。 */
    private val _bluetoothOnly = MutableStateFlow(
        preferences.getBoolean(BLUETOOTH_ONLY_KEY, true),
    )
    val bluetoothOnly: StateFlow<Boolean> = _bluetoothOnly.asStateFlow()

    fun setActiveModel(modelId: String) {
        val info = requireNotNull(WakeModelCatalog.byId(modelId)) { "Unknown wake model: $modelId" }
        _activeModelId.value = info.id
        preferences.edit { putString(ACTIVE_MODEL_KEY, info.id) }
    }

    fun setBluetoothOnly(enabled: Boolean) {
        _bluetoothOnly.value = enabled
        preferences.edit { putBoolean(BLUETOOTH_ONLY_KEY, enabled) }
    }

    private companion object {
        const val PREFERENCES_NAME = "bluetooth_voice_preferences"
        const val ACTIVE_MODEL_KEY = "active_model_id"
        const val BLUETOOTH_ONLY_KEY = "bluetooth_only"
    }
}
