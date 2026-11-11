package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.data.voice.remote.speechRequestBaseUrl
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession
import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
import github.ponyhuang.gimi.domain.voice.repository.SynthesisSessionRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class DefaultSpeechSynthesisRepository @Inject constructor(
    private val modelCatalog: ModelCatalogRepository,
    private val registry: VoiceCapabilityRegistry,
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
        val config = currentConfig() ?: error("请先在设置中选择可用的语音合成模型")
        val synthesizer = registry.synthesizer(config.serviceId)
        return synthesizer.synthesize(
            SynthesisRequest(
                text = normalized,
                voiceId = config.voiceId,
                modelId = config.modelId,
                apiKey = config.apiKey,
                baseUrl = speechRequestBaseUrl(config.serviceId, config.baseUrl, config.workspaceId),
                workspaceId = config.workspaceId,
            ),
        )
    }

    override fun cacheIdentity(): String? = currentConfig()?.let {
        "${it.baseUrl}|${it.modelId}|${it.voiceId}|${it.workspaceId.orEmpty()}"
    }

    /**
     * 打开一次会话式合成。
     *
     * 当前服务未接入会话式合成时返回 null，调用方据此回退到一次性合成：
     * 服务端按连接计费的厂商（如百炼）一次请求一次连接，会话式合成对它们没有收益。
     */
    override fun openSession(): SpeechSynthesisSession? {
        val config = currentConfig() ?: return null
        val synthesizer = registry.streamingSynthesizerOrNull(config.serviceId) ?: return null
        return synthesizer.open(
            SynthesisSessionRequest(
                voiceId = config.voiceId,
                modelId = config.modelId,
                apiKey = config.apiKey,
                baseUrl = speechRequestBaseUrl(config.serviceId, config.baseUrl, config.workspaceId),
                workspaceId = config.workspaceId,
            ),
        )
    }

    /** 当前选中的合成模型及其服务连接信息。 */
    private data class ServiceSynthesisConfig(
        val serviceId: String,
        val voiceId: String,
        val modelId: String,
        val apiKey: String,
        val baseUrl: String,
        /** 业务空间 ID（百炼）；厂商自有端点据此构造专属域名。 */
        val workspaceId: String?,
    )

    private fun currentConfig(): ServiceSynthesisConfig? {
        val resolved = modelCatalog.currentServices().resolve(
            modelCatalog.currentTtsSelection(),
            isSpeech = false,
        ) ?: return null
        return ServiceSynthesisConfig(
            serviceId = resolved.first.id,
            voiceId = modelCatalog.currentTtsVoice(),
            modelId = resolved.second.id,
            apiKey = resolved.first.apiKey.substringBefore(',').trim(),
            baseUrl = resolved.first.configuredBaseUrl,
            workspaceId = resolved.first.workspaceId,
        )
    }
}
