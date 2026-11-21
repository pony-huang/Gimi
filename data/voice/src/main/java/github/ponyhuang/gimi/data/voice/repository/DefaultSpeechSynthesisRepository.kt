package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession
import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
import github.ponyhuang.gimi.domain.voice.repository.SynthesisSessionRequest
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
import github.ponyhuang.gimi.domain.voice.repository.speechRequestBaseUrl
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class DefaultSpeechSynthesisRepository @Inject constructor(
    private val modelCatalog: ModelCatalogRepository,
    private val registry: VoiceCapabilityRegistry,
    private val voiceCatalog: VoiceCatalogResolver,
) : SpeechSynthesisRepository {
    override val availability: Flow<Boolean> = combine(
        modelCatalog.observeInstances(),
        modelCatalog.observeTtsSelection(),
    ) { _, _ ->
        isAvailable()
    }.distinctUntilChanged()

    override fun isAvailable(selection: ModelSelection?): Boolean =
        currentConfig(selection) != null

    override fun synthesize(
        text: String,
        voiceId: String?,
        selection: ModelSelection?,
    ): Flow<ByteArray> {
        val normalized = text.trim().takeIf(String::isNotEmpty)
            ?: error("没有可朗读的回复内容")
        val config = currentConfig(selection) ?: error("请先在设置中选择可用的语音合成模型")
        val synthesizer = registry.synthesizer(checkNotNull(config.templateId))
        return synthesizer.synthesize(
            SynthesisRequest(
                text = normalized,
                voiceId = resolveVoiceId(config.templateId, config.modelId, voiceId, selection),
                modelId = config.modelId,
                apiKey = config.apiKey,
                baseUrl = speechRequestBaseUrl(config.templateId, config.endpoint, config.workspaceId),
                workspaceId = config.workspaceId,
            ),
        )
    }

    override fun cacheIdentity(): String? = currentConfig()?.let {
        "${it.endpoint}|${it.modelId}|${resolveVoiceId(it.templateId, it.modelId, null)}|${it.workspaceId.orEmpty()}"
    }

    /**
     * 打开一次会话式合成。
     *
     * 当前服务未接入会话式合成时返回 null，调用方据此回退到一次性合成：
     * 服务端按连接计费的厂商（如百炼）一次请求一次连接，会话式合成对它们没有收益。
     */
    override fun openSession(voiceId: String?, selection: ModelSelection?): SpeechSynthesisSession? {
        val config = currentConfig(selection) ?: return null
        val synthesizer = registry.streamingSynthesizerOrNull(checkNotNull(config.templateId)) ?: return null
        return synthesizer.open(
            SynthesisSessionRequest(
                voiceId = resolveVoiceId(config.templateId, config.modelId, voiceId, selection),
                modelId = config.modelId,
                apiKey = config.apiKey,
                baseUrl = speechRequestBaseUrl(config.templateId, config.endpoint, config.workspaceId),
                workspaceId = config.workspaceId,
            ),
        )
    }

    /**
     * 生效音色：会话级覆盖优先；未覆盖时用全局配置，仍未配置回落到当前模型/服务的
     * 默认内置音色，避免把无效音色发给厂商触发运行期合成失败（MiniMax 2054）。
     * 无内置表的服务（openaicompatible 系）按空值交给服务端默认。
     */
    private fun resolveVoiceId(
        templateId: String?,
        modelId: String,
        override: String?,
        selection: ModelSelection? = null,
    ): String {
        override?.takeIf(String::isNotBlank)?.let { return it }
        val stored = modelCatalog.currentTtsVoice().trim().takeIf {
            selection == null || selection == modelCatalog.currentTtsSelection()
        }.orEmpty()
        if (stored.isNotEmpty()) return stored
        return voiceCatalog.builtInDefaultVoice(templateId, modelId)?.id.orEmpty()
    }

    /** 当前选中的合成连接；合成模型按 [ModelCapability.TTS] 标记校验。 */
    private fun currentConfig(selection: ModelSelection? = null): ResolvedServiceConnection? =
        modelCatalog.resolveVoiceConnection(selection ?: modelCatalog.currentTtsSelection()) { model ->
            ModelCapability.TTS in model.capabilities
        }
}
