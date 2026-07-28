package github.ponyhuang.asssistantai.feature.modelsettings.defaults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.isBusy
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.asssistantai.domain.speech.model.TtsVoiceCatalog
import github.ponyhuang.asssistantai.feature.modelsettings.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DefaultModelSettingsViewModel @Inject constructor(
    observeSettings: ObserveDefaultModelSettingsUseCase,
    private val updateSettings: UpdateDefaultModelSettingsUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
) : ViewModel() {
    private val dialog = MutableStateFlow<DefaultModelDialog?>(null)

    // 缓冲若干条一次性反馈，避免 Route 尚未开始收集时丢失。
    private val _effects = MutableSharedFlow<DefaultModelSettingsEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    val uiState = combine(observeSettings(), dialog, runWhenAgentIdle.state) {
            settings, currentDialog, runtimeState ->
        settings.toUiState(
            dialog = currentDialog,
            isMutationBlocked = runtimeState.isBusy,
        )
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
                mutate { updateSettings.voice(action.voiceId) }
            }
            is DefaultModelSettingsAction.SelectModel -> {
                mutate { select(action.target, action.selection) }
            }
        }
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch {
            when (runWhenAgentIdle { block() }) {
                is AgentMutationResult.Applied -> dialog.value = null
                AgentMutationResult.BlockedByActiveAgent -> _effects.emit(
                    DefaultModelSettingsEffect.ShowToast(
                        R.string.modelsettings_agent_mutation_blocked,
                    ),
                )
            }
        }
    }

    private fun select(target: DefaultModelDialog, selection: ModelSelection) {
        when (target) {
            DefaultModelDialog.Assistant -> updateSettings.assistant(selection)
            DefaultModelDialog.Fast -> updateSettings.fast(selection)
            DefaultModelDialog.Speech -> updateSettings.speech(selection)
            DefaultModelDialog.Tts -> {
                val currentServiceId = uiState.value.ttsSelection?.serviceId
                updateSettings.tts(selection)
                if (selection.serviceId != currentServiceId) {
                    val defaultVoice = TtsVoiceCatalog.forService(selection.serviceId)
                        .firstOrNull()
                        ?.id
                        .orEmpty()
                    updateSettings.voice(defaultVoice)
                }
            }
            DefaultModelDialog.TtsVoice -> Unit
        }
    }
}

private fun DefaultModelSettings.toUiState(
    dialog: DefaultModelDialog?,
    isMutationBlocked: Boolean,
): DefaultModelSettingsUiState {
    val configuredServices = services.filter { it.isEnabled && it.apiKey.isNotBlank() }
    return DefaultModelSettingsUiState(
        assistantSelection = assistantSelection,
        fastSelection = fastSelection,
        speechSelection = speechSelection,
        ttsSelection = ttsSelection,
        ttsVoiceId = ttsVoiceId,
        ttsVoiceOptions = TtsVoiceCatalog.forService(ttsSelection?.serviceId),
        chatModels = configuredServices.rows { !it.isStt && !it.isTts },
        speechModels = configuredServices.rows { it.isStt },
        ttsModels = configuredServices.rows { it.isTts },
        dialog = dialog,
        isMutationBlocked = isMutationBlocked,
    )
}

private fun List<github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting>.rows(
    predicate: (github.ponyhuang.asssistantai.domain.modelcatalog.model.Model) -> Boolean,
): List<SelectableModelRow> = flatMap { service ->
    service.groups.flatMap { group ->
        group.models.filter(predicate).map { model ->
            SelectableModelRow(service, group, model)
        }
    }
}
