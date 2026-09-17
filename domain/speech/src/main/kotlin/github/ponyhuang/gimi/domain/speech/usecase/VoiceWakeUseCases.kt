package github.ponyhuang.gimi.domain.speech.usecase

import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
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
            configurationReady = models.hasChatModel(),
        )
    }
}

class ManageVoiceWakeUseCase @Inject constructor(
    private val repository: VoiceWakeRepository,
) {
    fun setEnabled(enabled: Boolean) = repository.setEnabled(enabled)

    fun addTriggerPhrase(phrase: String) = repository.addTriggerPhrase(phrase)

    fun removeTriggerPhrase(phrase: String) = repository.removeTriggerPhrase(phrase)
}

private fun DefaultModelSettings.hasChatModel(): Boolean =
    services.filter { it.isEnabled && it.apiKey.isNotBlank() }.any { service ->
        service.groups.any { group ->
            group.models.any { model -> !model.isStt && !model.isTts }
        }
    }
