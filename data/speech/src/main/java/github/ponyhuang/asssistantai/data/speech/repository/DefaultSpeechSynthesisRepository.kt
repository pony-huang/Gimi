package github.ponyhuang.asssistantai.data.speech.repository

import github.ponyhuang.asssistantai.data.speech.remote.SpeechSynthesisConfig
import github.ponyhuang.asssistantai.data.speech.remote.SpeechSynthesisGateway
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechSynthesisRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class DefaultSpeechSynthesisRepository @Inject constructor(
    private val modelCatalog: ModelCatalogRepository,
    private val gateway: SpeechSynthesisGateway,
) : SpeechSynthesisRepository {
    override val availability: Flow<Boolean> = combine(
        modelCatalog.observeServices(),
        modelCatalog.observeTtsSelection(),
    ) { services, selection ->
        services.resolve(selection, isSpeech = false) != null
    }.distinctUntilChanged()

    override fun isAvailable(): Boolean = currentConfig() != null

    override fun synthesize(text: String): Flow<ByteArray> {
        val normalized = text.trim().takeIf(String::isNotEmpty)
            ?: error("没有可朗读的回复内容")
        val config = currentConfig() ?: error("请先在设置中选择可用的默认语音播放模型")
        return gateway.synthesize(config, normalized)
    }

    override fun cacheIdentity(): String? = currentConfig()?.let {
        "${it.baseUrl}|${it.modelId}|${it.voiceId}"
    }

    private fun currentConfig(): SpeechSynthesisConfig? {
        val resolved = modelCatalog.currentServices().resolve(
            modelCatalog.currentTtsSelection(),
            isSpeech = false,
        ) ?: return null
        return SpeechSynthesisConfig(
            baseUrl = resolved.first.openAiCompatibleBaseUrl,
            apiKey = resolved.first.apiKey.substringBefore(',').trim(),
            modelId = resolved.second.id,
            voiceId = modelCatalog.currentTtsVoice(),
        )
    }
}
