package github.ponyhuang.gimi.domain.modelcatalog.usecase

import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
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
        combine(
            repository.observeTtsVoice(),
            repository.observeRealtimeSelection(),
            repository.observeRealtimeVoice(),
        ) { voiceId, realtime, realtimeVoice ->
            Triple(voiceId, realtime, realtimeVoice)
        },
    ) { partial, (voiceId, realtime, realtimeVoice) ->
        DefaultModelSettings(
            services = partial.services,
            assistantSelection = partial.assistant,
            fastSelection = partial.fast,
            speechSelection = partial.speech,
            ttsSelection = partial.tts,
            ttsVoiceId = voiceId,
            realtimeSelection = realtime,
            realtimeVoice = realtimeVoice,
        )
    }

    private data class PartialSettings(
        val services: List<github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting>,
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

    /** 选择 Realtime 默认模型；null 表示取消配置。 */
    fun realtimeModel(selection: ModelSelection?) = repository.selectRealtimeModel(selection)

    /** 设置 Realtime 音色覆盖；null 表示回到模型缺省音色。 */
    fun realtimeVoice(override: github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride?) =
        repository.selectRealtimeVoice(override)
}
