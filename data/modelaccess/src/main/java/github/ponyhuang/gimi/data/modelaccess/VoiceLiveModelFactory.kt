package github.ponyhuang.gimi.data.modelaccess

import com.google.adk.kt.models.Model
import github.ponyhuang.gimi.core.network.SpeechWebSocketClient
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import github.ponyhuang.gimi.data.modelaccess.qwen.realtime.QwenRealtimeModel
import github.ponyhuang.gimi.data.modelaccess.qwen.realtime.QwenRealtimeProtocol
import github.ponyhuang.gimi.data.modelaccess.openai.realtime.OpenAiRealtimeModel
import github.ponyhuang.gimi.data.modelaccess.openai.realtime.OpenAiRealtimeProtocol

/**
 * 语音模型的唯一装配点，返回 ADK 官方 Model，而不是新的供应商会话接口。
 * 所选模型携带 [ModelCapability.Realtime] 标记时按模板身份分流到原生全双工适配器
 * （[OpenAiRealtimeModel] / [QwenRealtimeModel]）；其余继续使用配置中的
 * ASR/文字模型/TTS 级联（[CascadedVoiceModel]）。实时与级联连接共处同一服务实例，
 * 凭据按连接各自解析（共享或独立覆盖）。
 *
 * @param capabilities 语音能力解析器；原生 Realtime 的上行契约与缺省音色由适配器自述，
 *   工厂不再按厂商硬编码差异。
 */
class VoiceLiveModelFactory @Inject constructor(
    private val models: AgentLLMModelFactory,
    private val configuration: AgentModelConfigurationSource,
    private val modelCatalog: ModelCatalogRepository,
    private val recognition: SpeechRecognitionRepository,
    private val synthesis: SpeechSynthesisRepository,
    private val capabilities: VoiceCapabilityResolver,
    @SpeechWebSocketClient private val webSocketClient: OkHttpClient,
) {
    /** 解析实时语音连接；选择缺失、服务停用、凭据为空或模型非实时时为 null。 */
    private fun realtimeConnectionFor(selection: ModelSelection?): ResolvedServiceConnection? =
        modelCatalog.resolveConnection(selection)
            ?.takeIf { ModelCapability.Realtime in it.model.capabilities }

    /**
     * Realtime 会话的上行契约；所选模型非实时或该模板无原生适配器时为 null
     * （后者在 [create] 中回落级联，门面不应为其建立全双工会话）。
     */
    suspend fun realtimeUplink(selection: ModelSelection?): RealtimeUplink? {
        configuration.awaitReady()
        val connection = realtimeConnectionFor(selection) ?: return null
        // 上行采样率随厂商协议而异，由适配器自述；未接入原生适配器的模板没有全双工契约。
        val templateId = connection.templateId ?: return null
        return capabilities.realtimeSupport(templateId)?.uplink
    }

    suspend fun isAvailable(selection: ModelSelection? = null): Boolean {
        configuration.awaitReady()
        if (realtimeConnectionFor(selection) != null) {
            // resolveConnection 已校验启用状态、凭据与模型存在性。
            return true
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
        val connection = realtimeConnectionFor(selection)
        if (connection != null) {
            val modelId = checkNotNull(selection?.modelId) { "Realtime 模型选择缺失" }
            val support = connection.templateId?.let { capabilities.realtimeSupport(it) }
            return when (connection.templateId) {
                "qwen" -> QwenRealtimeModel(
                    // 端点由厂商自有：官方默认域名写死，业务空间 ID 非空时改写专属域名。
                    endpoint = SpeechEndpoints.qwenRealtimeUrl(connection.workspaceId),
                    model = modelId,
                    apiKey = connection.apiKey,
                    voice = resolveVoice(support, modelId, voice),
                    silenceDurationMs = QwenRealtimeProtocol.DEFAULT_SILENCE_DURATION_MS,
                    workspaceId = connection.workspaceId,
                    webSocketClient = webSocketClient,
                )
                "openai" -> OpenAiRealtimeModel(
                    endpoint = SpeechEndpoints.OPENAI_REALTIME_DEFAULT,
                    model = modelId,
                    apiKey = connection.apiKey,
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
     * 不能只按模板取一个值。
     */
    private fun resolveVoice(
        support: RealtimeVoiceSupport?,
        modelId: String,
        override: String?,
    ): String = override?.takeIf { it.isNotBlank() }
        ?: support?.defaultVoiceFor(modelId)
        ?: support?.builtInVoices?.firstOrNull()?.id
        ?: error("Realtime 模板未声明会话音色：$modelId")

    private suspend fun cascadedModel(selection: ModelSelection?): Model {
        check(selection == null || configuration.resolveChatModel(selection) != null) { "Chat model unavailable" }
        return CascadedVoiceModel(models.createModel(models.selectModelConfig(selection)), recognition, synthesis)
    }
}
