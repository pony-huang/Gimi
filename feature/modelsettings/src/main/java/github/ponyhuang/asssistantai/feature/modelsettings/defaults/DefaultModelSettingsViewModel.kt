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
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private val notice = MutableStateFlow<String?>(null)

    val uiState = combine(observeSettings(), dialog, runWhenAgentIdle.state, notice) {
            settings, currentDialog, runtimeState, currentNotice ->
        settings.toUiState(
            dialog = currentDialog,
            isMutationBlocked = runtimeState.isBusy,
            notice = currentNotice,
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
                is AgentMutationResult.Applied -> {
                    notice.value = null
                    dialog.value = null
                }
                AgentMutationResult.BlockedByActiveAgent -> notice.value = BLOCKED_MESSAGE
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

    private companion object {
        const val BLOCKED_MESSAGE = ""
    }
}

private fun DefaultModelSettings.toUiState(
    dialog: DefaultModelDialog?,
    isMutationBlocked: Boolean,
    notice: String?,
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
        isMutationBlocked = isMutationBlocked,
        notice = notice,
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
