package github.ponyhuang.asssistantai.domain.modelcatalog.usecase

import github.ponyhuang.asssistantai.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.combine

class ObserveDefaultModelSettingsUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
) {
    operator fun invoke() = combine(
        combine(
            repository.observeServices(),
            repository.observeAssistantSelection(),
            repository.observeFastSelection(),
            repository.observeSpeechSelection(),
            repository.observeTtsSelection(),
        ) { services, assistant, fast, speech, tts ->
            PartialSettings(services, assistant, fast, speech, tts)
        },
        repository.observeTtsVoice(),
    ) { partial, voiceId ->
        DefaultModelSettings(
            services = partial.services,
            assistantSelection = partial.assistant,
            fastSelection = partial.fast,
            speechSelection = partial.speech,
            ttsSelection = partial.tts,
            ttsVoiceId = voiceId,
        )
    }

    private data class PartialSettings(
        val services: List<github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting>,
        val assistant: ModelSelection?,
        val fast: ModelSelection?,
        val speech: ModelSelection?,
        val tts: ModelSelection?,
    )
}

class UpdateDefaultModelSettingsUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
) {
    fun assistant(selection: ModelSelection) = repository.selectAssistantModel(selection)
    fun fast(selection: ModelSelection) = repository.selectFastModel(selection)
    fun speech(selection: ModelSelection) = repository.selectSpeechModel(selection)
    fun tts(selection: ModelSelection) = repository.selectTtsModel(selection)
    fun voice(voiceId: String) = repository.selectTtsVoice(voiceId)
}
