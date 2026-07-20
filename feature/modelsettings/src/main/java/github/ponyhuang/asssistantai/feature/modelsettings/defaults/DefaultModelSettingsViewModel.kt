package github.ponyhuang.asssistantai.feature.modelsettings.defaults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DefaultModelSettingsViewModel @Inject constructor(
    observeSettings: ObserveDefaultModelSettingsUseCase,
    private val updateSettings: UpdateDefaultModelSettingsUseCase,
) : ViewModel() {
    private val dialog = MutableStateFlow<DefaultModelDialog?>(null)

    val uiState = combine(observeSettings(), dialog) { settings, currentDialog ->
        settings.toUiState(currentDialog)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DefaultModelSettingsUiState(),
    )

    fun onAction(action: DefaultModelSettingsAction) {
        when (action) {
            is DefaultModelSettingsAction.ShowDialog -> dialog.value = action.dialog
            DefaultModelSettingsAction.DismissDialog -> dialog.value = null
            is DefaultModelSettingsAction.SelectVoice -> {
                updateSettings.voice(action.voiceId)
                dialog.value = null
            }
            is DefaultModelSettingsAction.SelectModel -> {
                select(action.target, action.selection)
                dialog.value = null
            }
        }
    }

    private fun select(target: DefaultModelDialog, selection: ModelSelection) {
        when (target) {
            DefaultModelDialog.Assistant -> updateSettings.assistant(selection)
            DefaultModelDialog.Fast -> updateSettings.fast(selection)
            DefaultModelDialog.Speech -> updateSettings.speech(selection)
            DefaultModelDialog.Tts -> updateSettings.tts(selection)
            DefaultModelDialog.TtsVoice -> Unit
        }
    }
}

private fun DefaultModelSettings.toUiState(
    dialog: DefaultModelDialog?,
): DefaultModelSettingsUiState {
    val configuredServices = services.filter { it.isEnabled && it.apiKey.isNotBlank() }
    return DefaultModelSettingsUiState(
        assistantSelection = assistantSelection,
        fastSelection = fastSelection,
        speechSelection = speechSelection,
        ttsSelection = ttsSelection,
        ttsVoiceId = ttsVoiceId,
        chatModels = configuredServices.rows { !it.isStt && !it.isTts },
        speechModels = configuredServices.rows { it.isStt },
        ttsModels = configuredServices.rows { it.isTts },
        dialog = dialog,
    )
}

private fun List<github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService>.rows(
    predicate: (github.ponyhuang.asssistantai.domain.modelcatalog.model.Model) -> Boolean,
): List<SelectableModelRow> = flatMap { service ->
    service.groups.flatMap { group ->
        group.models.filter(predicate).map { model ->
            SelectableModelRow(service, group, model)
        }
    }
}
