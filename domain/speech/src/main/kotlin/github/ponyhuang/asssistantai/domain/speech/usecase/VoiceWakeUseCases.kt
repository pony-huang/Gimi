package github.ponyhuang.asssistantai.domain.speech.usecase

import github.ponyhuang.asssistantai.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeSettings
import github.ponyhuang.asssistantai.domain.speech.repository.VoiceWakeRepository
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
    fun setKeyword(keyword: String) = repository.setKeyword(keyword)

    fun installModel() = repository.installModel()

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

private fun List<github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService>.containsSelection(
    selection: ModelSelection?,
    predicate: (github.ponyhuang.asssistantai.domain.modelcatalog.model.Model) -> Boolean,
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
