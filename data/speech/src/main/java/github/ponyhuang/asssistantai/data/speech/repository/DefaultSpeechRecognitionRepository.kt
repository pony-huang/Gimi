package github.ponyhuang.asssistantai.data.speech.repository

import github.ponyhuang.asssistantai.data.speech.remote.SpeechRecognitionConfig
import github.ponyhuang.asssistantai.data.speech.remote.SpeechRecognitionGateway
import github.ponyhuang.asssistantai.data.speech.remote.SpeechRecognitionRequest
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechRecognitionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class DefaultSpeechRecognitionRepository @Inject constructor(
    private val modelCatalog: ModelCatalogRepository,
    private val gateway: SpeechRecognitionGateway,
) : SpeechRecognitionRepository {
    override val availability: Flow<Boolean> = combine(
        modelCatalog.observeServices(),
        modelCatalog.observeSpeechSelection(),
    ) { services, selection ->
        resolveConfig(services, selection) != null
    }.distinctUntilChanged()

    override suspend fun transcribe(pcm16: ByteArray): String {
        require(pcm16.isNotEmpty()) { "没有录制到语音，请重试" }
        val config = currentConfig() ?: error("请先在设置中选择可用的默认语音模型")
        return gateway.transcribe(
            config = config,
            request = SpeechRecognitionRequest(pcm16 = pcm16),
        ).trim().takeIf { it.isNotEmpty() } ?: error("语音识别未返回文本")
    }

    private fun currentConfig(): SpeechRecognitionConfig? = resolveConfig(
        modelCatalog.currentServices(),
        modelCatalog.currentSpeechSelection(),
    )

    private fun resolveConfig(
        services: List<LLMModelSetting>,
        selection: ModelSelection?,
    ): SpeechRecognitionConfig? {
        val resolved = services.resolve(selection, isSpeech = true) ?: return null
        return SpeechRecognitionConfig(
            baseUrl = resolved.first.openAiCompatibleBaseUrl,
            apiKey = resolved.first.apiKey.trim(),
            modelId = resolved.second.id,
        )
    }
}

internal fun List<LLMModelSetting>.resolve(
    selection: ModelSelection?,
    isSpeech: Boolean,
): Pair<LLMModelSetting, github.ponyhuang.asssistantai.domain.modelcatalog.model.Model>? {
    if (selection == null) return null
    val service = firstOrNull { it.id == selection.serviceId } ?: return null
    if (!service.isEnabled || service.apiKey.isBlank()) return null
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return null
    val model = group.models.firstOrNull { it.id == selection.modelId } ?: return null
    if (if (isSpeech) !model.isStt else !model.isTts) return null
    return service to model
}
