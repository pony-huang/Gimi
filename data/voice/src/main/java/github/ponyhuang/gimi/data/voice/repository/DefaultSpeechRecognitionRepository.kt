package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.data.modelaccess.shared.speechRequestBaseUrl
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.recognitionCapability
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
        modelCatalog.observeInstances(),
        modelCatalog.observeSpeechSelection(),
    ) { _, _ ->
        currentConfig() != null
    }.distinctUntilChanged()

    override suspend fun transcribe(
        pcm16: ByteArray,
        context: List<SpeechContextMessage>,
    ): String {
        require(pcm16.isNotEmpty()) { "没有录制到语音，请重试" }
        val config = currentConfig() ?: error("请先在设置中选择可用的语音识别模型")
        // 适配器按模板标识分派；模型声明的识别能力（专用转写或音频理解）决定走哪套协议。
        val recognizer = registry.recognizer(checkNotNull(config.templateId), checkNotNull(config.recognitionCapability))
        return recognizer.recognize(
            RecognitionRequest(
                pcm16 = pcm16,
                modelId = config.modelId,
                apiKey = config.apiKey,
                baseUrl = speechRequestBaseUrl(config.templateId, config.endpoint, config.workspaceId),
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
            baseUrl = speechRequestBaseUrl(config.templateId, config.endpoint, config.workspaceId),
            context = context,
            workspaceId = config.workspaceId,
        )
        val capability = checkNotNull(config.recognitionCapability)
        val templateId = checkNotNull(config.templateId)
        val streaming = registry.streamingRecognizerOrNull(templateId, capability)
        if (streaming != null) {
            streaming.recognize(request).collect { emit(it) }
        } else {
            val text = registry.recognizer(templateId, capability).recognize(request)
            emit(RecognitionEvent.Final(text))
        }
    }

    /** 当前选中的识别连接；能力校验在连接视图解析后进行。 */
    private fun currentConfig(): ResolvedServiceConnection? =
        modelCatalog.resolveVoiceConnection(modelCatalog.currentSpeechSelection()) { model ->
            model.recognitionCapability != null
        }
}
