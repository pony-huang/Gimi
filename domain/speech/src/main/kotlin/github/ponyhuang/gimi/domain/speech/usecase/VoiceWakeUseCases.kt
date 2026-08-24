package github.ponyhuang.gimi.domain.speech.usecase

import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeSettings
import github.ponyhuang.gimi.domain.speech.repository.VoiceWakeRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.combine

class ObserveVoiceWakeSettingsUseCase @Inject constructor(
    observeDefaultModels: ObserveDefaultModelSettingsUseCase,
    private val repository: VoiceWakeRepository,
) {
    private val defaultModels = observeDefaultModels()

    operator fun invoke() = combine(defaultModels, repository.state) { models, voiceState ->
        VoiceWakeSettings(
            voiceState = voiceState,
            configurationReady = models.isVoiceWakeConfigurationReady(),
        )
    }
}

class ManageVoiceWakeUseCase @Inject constructor(
    private val repository: VoiceWakeRepository,
) {
    fun selectModel(modelId: String) = repository.selectModel(modelId)

    fun installModel(modelId: String) = repository.installModel(modelId)

    fun cancelInstall(modelId: String) = repository.cancelInstall(modelId)

    fun removeModel(modelId: String) {
        val state = repository.state.value
        if (state.activeModelId == modelId && state.isRunning) {
            repository.stop()
        }
        repository.removeModel(modelId)
    }

    fun start() = repository.start()

    fun stop() = repository.stop()
}

private fun DefaultModelSettings.isVoiceWakeConfigurationReady(): Boolean {
    val configuredServices = services.filter { it.isEnabled && it.apiKey.isNotBlank() }
    val hasChatModel = configuredServices.any { service ->
        service.groups.any { group ->
            group.models.any { model -> !model.isStt && !model.isTts }
        }
    }
    val hasSelectedSpeechModel = configuredServices.containsSelection(
        selection = speechSelection,
        predicate = { it.isStt },
    )
    return hasChatModel && hasSelectedSpeechModel
}

private fun List<github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting>.containsSelection(
    selection: ModelSelection?,
    predicate: (github.ponyhuang.gimi.domain.modelcatalog.model.Model) -> Boolean,
): Boolean {
    if (selection == null) return false
    return any { service ->
        service.id == selection.serviceId && service.groups.any { group ->
            group.id == selection.groupId && group.models.any { model ->
                model.id == selection.modelId && predicate(model)
            }
        }
    }
}
