package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.models.Model
import github.ponyhuang.gimi.core.network.SpeechWebSocketClient
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * 语音模型的唯一装配点，返回 ADK 官方 Model，而不是新的供应商会话接口。
 * 所选模型携带 [ModelCapability.Realtime] 标记时按 serviceId 分流到原生全双工适配器
 * （[OpenAiRealtimeModel] / [QwenRealtimeModel]）；其余继续使用配置中的
 * ASR/文字模型/TTS 级联（[CascadedVoiceModel]）。实时与级联模型共处同一语音服务，
 * 共用一套密钥与业务空间配置。
 *
 * @param capabilities 语音能力解析器；原生 Realtime 的上行契约与缺省音色由适配器自述，
 *   工厂不再按 serviceId 硬编码厂商差异。
 */
class VoiceLiveModelFactory @Inject constructor(
    private val models: AgentLLMModelFactory,
    private val configuration: AgentModelConfigurationSource,
    private val recognition: SpeechRecognitionRepository,
    private val synthesis: SpeechSynthesisRepository,
    private val capabilities: VoiceCapabilityResolver,
    @SpeechWebSocketClient private val webSocketClient: OkHttpClient,
) {
    /** 按 serviceId 找当前服务配置；目录未就绪或服务被移除时为 null。 */
    private fun serviceFor(selection: ModelSelection?) =
        selection?.let { s -> configuration.currentServices().firstOrNull { it.id == s.serviceId } }

    /** 所选模型是否为原生实时语音模型；实时与级联模型靠该标记分派，不再靠服务种类。 */
    private fun realtimeModelFor(selection: ModelSelection?, service: LLMModelSetting): LLMModelSetting? =
        selection?.let { s ->
            service.groups.firstOrNull { it.id == s.groupId }?.models
                ?.firstOrNull { it.id == s.modelId && ModelCapability.Realtime in it.capabilities }
        }?.let { service }

    /**
     * Realtime 会话的上行契约；所选模型非实时或该服务无原生适配器时为 null
     * （后者在 [create] 中回落级联，门面不应为其建立全双工会话）。
     */
    internal suspend fun realtimeUplink(selection: ModelSelection?): RealtimeUplink? {
        configuration.awaitReady()
        val service = serviceFor(selection) ?: return null
        if (realtimeModelFor(selection, service) == null) return null
        // 上行采样率随厂商协议而异，由适配器自述；未接入原生适配器的服务没有全双工契约。
        return capabilities.realtimeSupport(service.id)?.uplink
    }

    suspend fun isAvailable(selection: ModelSelection? = null): Boolean {
        configuration.awaitReady()
        val service = serviceFor(selection)
        if (service != null && realtimeModelFor(selection, service) != null) {
            return service.isEnabled && service.isAdded && service.apiKey.isNotBlank()
        }
        return (selection == null || configuration.resolveChatModel(selection) != null) &&
            synthesis.isAvailable() && recognition.availability.first()
    }

    /**
     * 装配语音模型。
     *
     * @param selection 模型选择；null 表示用当前配置里的默认聊天模型。
     * @param voice Realtime 会话音色的自定义覆盖；null 表示使用所选模型在该厂商下的缺省音色。
     */
    suspend fun create(selection: ModelSelection? = null, voice: String? = null): Model {
        configuration.awaitReady()
        val service = serviceFor(selection)
        if (service != null && realtimeModelFor(selection, service) != null) {
            check(service.isAdded && service.apiKey.isNotBlank()) { "Realtime 服务未配置密钥" }
            val modelId = checkNotNull(selection?.modelId) { "Realtime 模型选择缺失" }
            // 与语音能力层一致的密钥惯例：多 key 以逗号分隔，取首个。
            val apiKey = service.apiKey.substringBefore(',').trim()
            val support = capabilities.realtimeSupport(service.id)
            return when (service.id) {
                QWEN_AUDIO_SERVICE_ID -> QwenRealtimeModel(
                    // 端点由厂商自有：官方默认域名写死，业务空间 ID 非空时改写专属域名。
                    endpoint = SpeechEndpoints.qwenRealtimeUrl(service.workspaceId),
                    model = modelId,
                    apiKey = apiKey,
                    voice = resolveVoice(support, modelId, voice),
                    silenceDurationMs = QwenRealtimeProtocol.DEFAULT_SILENCE_DURATION_MS,
                    workspaceId = service.workspaceId,
                    webSocketClient = webSocketClient,
                )
                OPENAI_SPEECH_SERVICE_ID -> OpenAiRealtimeModel(
                    endpoint = SpeechEndpoints.OPENAI_REALTIME_DEFAULT,
                    model = modelId,
                    apiKey = apiKey,
                    voice = resolveVoice(support, modelId, voice),
                    silenceDurationMs = OpenAiRealtimeProtocol.DEFAULT_SILENCE_DURATION_MS,
                    webSocketClient = webSocketClient,
                )
                // 未知厂商的实时模型无原生适配器；防御性回落级联（其模型未声明
                // STT/TTS 能力，实际会话会在级联可用性检查处失败并暴露配置问题）。
                else -> cascadedModel(selection)
            }
        }
        return cascadedModel(selection)
    }

    /**
     * 解析最终下发的会话音色：自定义覆盖优先，否则用该模型在本厂商下的缺省音色。
     *
     * 音色与模型强绑定（同一厂商不同代际的合法音色集互不相同），因此缺省音色按模型解析，
     * 不能只按服务取一个值。
     */
    private fun resolveVoice(
        support: RealtimeVoiceSupport?,
        modelId: String,
        override: String?,
    ): String = override?.takeIf { it.isNotBlank() }
        ?: support?.defaultVoiceFor(modelId)
        ?: support?.builtInVoices?.firstOrNull()?.id
        ?: error("Realtime 服务未声明会话音色：$modelId")

    private suspend fun cascadedModel(selection: ModelSelection?): Model {
        check(selection == null || configuration.resolveChatModel(selection) != null) { "Chat model unavailable" }
        return CascadedVoiceModel(models.createModel(models.selectModelConfig(selection)), recognition, synthesis)
    }
}
