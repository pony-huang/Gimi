package github.ponyhuang.asssistantai.feature.modelsettings.defaults

import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService

data class SelectableModelRow(
    val service: ModelService,
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
    val chatModels: List<SelectableModelRow> = emptyList(),
    val speechModels: List<SelectableModelRow> = emptyList(),
    val ttsModels: List<SelectableModelRow> = emptyList(),
    val dialog: DefaultModelDialog? = null,
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
