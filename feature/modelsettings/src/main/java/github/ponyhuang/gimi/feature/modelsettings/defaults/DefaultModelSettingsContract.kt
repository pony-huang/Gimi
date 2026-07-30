package github.ponyhuang.gimi.feature.modelsettings.defaults

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.speech.model.TtsVoice

data class SelectableModelRow(
    val service: LLMModelSetting,
    val group: ModelGroup,
    val model: Model,
) {
    fun selection() = ModelSelection(service.id, group.id, model.id)
}

data class DefaultModelSettingsUiState(
    val assistantSelection: ModelSelection? = null,
    val fastSelection: ModelSelection? = null,
    val speechSelection: ModelSelection? = null,
    val ttsSelection: ModelSelection? = null,
    val ttsVoiceId: String = "",
    val ttsVoiceOptions: List<TtsVoice> = emptyList(),
    val chatModels: List<SelectableModelRow> = emptyList(),
    val speechModels: List<SelectableModelRow> = emptyList(),
    val ttsModels: List<SelectableModelRow> = emptyList(),
    val dialog: DefaultModelDialog? = null,
    val isMutationBlocked: Boolean = false,
)

enum class DefaultModelDialog {
    Assistant,
    Fast,
    Speech,
    Tts,
    TtsVoice,
}

sealed interface DefaultModelSettingsAction {
    data class ShowDialog(val dialog: DefaultModelDialog) : DefaultModelSettingsAction
    data class SelectModel(
        val target: DefaultModelDialog,
        val selection: ModelSelection,
    ) : DefaultModelSettingsAction
    data class SelectVoice(val voiceId: String) : DefaultModelSettingsAction
    data object DismissDialog : DefaultModelSettingsAction
}

/** 一次性 UI 反馈（Toast 等），由 Route 经 effects 通道消费。 */
sealed interface DefaultModelSettingsEffect {
    /** @param messageRes 文案的 string resource id。 */
    data class ShowToast(@StringRes val messageRes: Int) : DefaultModelSettingsEffect
}
