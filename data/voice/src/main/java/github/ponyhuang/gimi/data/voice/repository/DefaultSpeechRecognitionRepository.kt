package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.data.voice.remote.speechRequestBaseUrl
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.isRecognition
import github.ponyhuang.gimi.domain.modelcatalog.model.recognitionCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.model.SpeechContextMessage
import github.ponyhuang.gimi.domain.voice.repository.RecognitionEvent
import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

@Singleton
class DefaultSpeechRecognitionRepository @Inject constructor(
    private val modelCatalog: ModelCatalogRepository,
    private val registry: VoiceCapabilityRegistry,
) : SpeechRecognitionRepository {
    override val availability: Flow<Boolean> = combine(
        modelCatalog.observeServices(),
        modelCatalog.observeSpeechSelection(),
    ) { services, selection ->
        resolveConfig(services, selection) != null
    }.distinctUntilChanged()

    override suspend fun transcribe(
        pcm16: ByteArray,
        context: List<SpeechContextMessage>,
    ): String {
        require(pcm16.isNotEmpty()) { "没有录制到语音，请重试" }
        val config = currentConfig() ?: error("请先在设置中选择可用的语音识别模型")
        val recognizer = registry.recognizer(config.serviceId, config.capability)
        return recognizer.recognize(
            RecognitionRequest(
                pcm16 = pcm16,
                modelId = config.modelId,
                apiKey = config.apiKey,
                baseUrl = speechRequestBaseUrl(config.serviceId, config.baseUrl, config.workspaceId),
                context = context,
                workspaceId = config.workspaceId,
            ),
        ).trim().takeIf { it.isNotEmpty() } ?: error("语音识别未返回文本")
    }

    /**
     * 流式转写：产出中间结果与最终结果。
     *
     * 服务未接入流式识别时只产出最终结果，因此调用方可以统一按流消费，
     * 不必因为厂商差异改写识别分支。MiniMax 的识别端点只能整段上传，
     * 因此走的就是这条回退路径。
     */
    override fun transcribeStreaming(
        pcm16: ByteArray,
        context: List<SpeechContextMessage>,
    ): Flow<RecognitionEvent> = flow {
        require(pcm16.isNotEmpty()) { "没有录制到语音，请重试" }
        val config = currentConfig() ?: error("请先在设置中选择可用的语音识别模型")
        val request = RecognitionRequest(
            pcm16 = pcm16,
            modelId = config.modelId,
            apiKey = config.apiKey,
            baseUrl = speechRequestBaseUrl(config.serviceId, config.baseUrl, config.workspaceId),
            context = context,
            workspaceId = config.workspaceId,
        )
        val streaming = registry.streamingRecognizerOrNull(config.serviceId, config.capability)
        if (streaming != null) {
            streaming.recognize(request).collect { emit(it) }
        } else {
            val text = registry.recognizer(config.serviceId, config.capability).recognize(request)
            emit(RecognitionEvent.Final(text))
        }
    }

    /** 当前选中的识别模型及其服务连接信息。 */
    private data class ServiceRecognitionConfig(
        val serviceId: String,
        val modelId: String,
        val apiKey: String,
        val baseUrl: String,
        /** 业务空间 ID（百炼）；厂商自有端点据此构造专属域名。 */
        val workspaceId: String?,
        /** 模型声明的识别能力：专用转写服务或音频理解模型，决定走哪一套协议。 */
        val capability: ModelCapability,
    )

    private fun currentConfig(): ServiceRecognitionConfig? = resolveConfig(
        modelCatalog.currentServices(),
        modelCatalog.currentSpeechSelection(),
    )

    private fun resolveConfig(
        services: List<LLMModelSetting>,
        selection: ModelSelection?,
    ): ServiceRecognitionConfig? {
        val resolved = services.resolve(selection, isSpeech = true) ?: return null
        val model = resolved.second
        // 模型可同时具备两种识别语义时，优先走专用转写服务。
        val capability = model.recognitionCapability ?: return null
        return ServiceRecognitionConfig(
            serviceId = resolved.first.id,
            modelId = model.id,
            // 配置可能存逗号分隔的多个 key，统一取第一个，与合成路径一致
            apiKey = resolved.first.apiKey.substringBefore(',').trim(),
            baseUrl = resolved.first.configuredBaseUrl,
            workspaceId = resolved.first.workspaceId,
            capability = capability,
        )
    }
}

internal fun List<LLMModelSetting>.resolve(
    selection: ModelSelection?,
    isSpeech: Boolean,
): Pair<LLMModelSetting, github.ponyhuang.gimi.domain.modelcatalog.model.Model>? {
    if (selection == null) return null
    val service = firstOrNull { it.id == selection.serviceId } ?: return null
    if (!service.isEnabled || service.apiKey.isBlank()) return null
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return null
    val model = group.models.firstOrNull { it.id == selection.modelId } ?: return null
    if (if (isSpeech) !model.capabilities.any { it.isRecognition } else !model.capabilities.contains(ModelCapability.TTS)) {
        return null
    }
    return service to model
}
