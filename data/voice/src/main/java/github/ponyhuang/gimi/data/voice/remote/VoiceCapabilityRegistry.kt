package github.ponyhuang.gimi.data.voice.remote

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音能力注册表。
 *
 * 解析键是 `(服务标识, 能力)`：服务标识决定厂商，能力决定走哪一种协议实现。
 * 适配器由 Hilt 集合注入并自报所服务的服务与能力。
 *
 * 与旧的两条 `when(serviceId)` 链相比：
 * - 新增厂商只需实现适配器并注册，不再修改任何集中路由；
 * - 同一服务下的不同能力可以走不同协议（例如专用转写端点与对话协议音频理解）；
 * - 未注册的能力是显式失败，不再静默落到其它协议的兜底实现。
 */
@Singleton
class VoiceCapabilityRegistry @Inject constructor(
    synthesizers: Set<@JvmSuppressWildcards SpeechSynthesizer>,
    recognizers: Set<@JvmSuppressWildcards SpeechRecognizer>,
    streamingRecognizers: Set<@JvmSuppressWildcards StreamingSpeechRecognizer>,
    connectionProbes: Set<@JvmSuppressWildcards SpeechConnectionProbe>,
    streamingSynthesizers: Set<@JvmSuppressWildcards StreamingSpeechSynthesizer> = emptySet(),
    realtimeSupports: Set<@JvmSuppressWildcards RealtimeVoiceSupport> = emptySet(),
) : VoiceCapabilityResolver {

    private val synthesizers: List<SpeechSynthesizer> = synthesizers.toList()
    private val recognizers: List<SpeechRecognizer> = recognizers.toList()
    private val streamingRecognizers: List<StreamingSpeechRecognizer> = streamingRecognizers.toList()
    private val connectionProbes: List<SpeechConnectionProbe> = connectionProbes.toList()
    private val streamingSynthesizers: List<StreamingSpeechSynthesizer> = streamingSynthesizers.toList()
    private val realtimeSupports: List<RealtimeVoiceSupport> = realtimeSupports.toList()

    /**
     * 解析语音合成适配器。
     *
     * @throws VoiceCapabilityNotSupported 该服务未接入合成能力。
     */
    fun synthesizer(serviceId: String): SpeechSynthesizer =
        synthesizers.firstOrNull { serviceId in it.serviceIds }
            ?: throw VoiceCapabilityNotSupported(serviceId, ModelCapability.TTS)

    /**
     * 解析一次性语音识别适配器。
     *
     * @param capability 模型声明的识别能力（[ModelCapability.STT] 或 [ModelCapability.ASR]）。
     * @throws VoiceCapabilityNotSupported 该服务未以该能力接入识别。
     */
    fun recognizer(serviceId: String, capability: ModelCapability): SpeechRecognizer =
        recognizers.firstOrNull { serviceId in it.serviceIds && capability in it.capabilities }
            ?: throw VoiceCapabilityNotSupported(serviceId, capability)


    /**
     * 解析流式语音识别适配器。
     *
     * @throws VoiceCapabilityNotSupported 该服务不支持流式识别。
     */
    fun streamingRecognizer(serviceId: String): StreamingSpeechRecognizer =
        streamingRecognizers.firstOrNull { serviceId in it.serviceIds }
            ?: throw VoiceCapabilityNotSupported(serviceId, ModelCapability.STT)

    /**
     * 解析流式语音识别适配器；该服务未以该能力接入时返回 null。
     *
     * 调用方据此回退到一次性识别，而不是把"不支持流式识别"变成运行中的意外失败。
     */
    fun streamingRecognizerOrNull(
        serviceId: String,
        capability: ModelCapability,
    ): StreamingSpeechRecognizer? =
        streamingRecognizers.firstOrNull { serviceId in it.serviceIds && capability in it.capabilities }

    /**
     * 解析会话式语音合成适配器；该服务未接入时返回 null。
     *
     * 调用方据此回退到一次性合成，而不是把"不支持会话式合成"变成运行中的意外失败。
     */
    fun streamingSynthesizerOrNull(serviceId: String): StreamingSpeechSynthesizer? =
        streamingSynthesizers.firstOrNull { serviceId in it.serviceIds }

    /**
     * 解析该服务的连通性探测能力。
     *
     * @throws VoiceCapabilityNotSupported 该服务未实现连接测试。
     */
    override fun connectionProbe(serviceId: String): SpeechConnectionProbe =
        connectionProbes.firstOrNull { serviceId in it.serviceIds }
            ?: throw VoiceCapabilityNotSupported(serviceId, ModelCapability.TTS)

    /**
     * 该服务的端点来源策略。
     *
     * 同一服务下不同能力的策略可能不同（例如合成走用户基址、识别走厂商自有端点），
     * 因此按能力解析；该服务没有任何适配器时按用户可配置处理。
     */
    override fun endpointStrategy(serviceId: String): EndpointStrategy =
        (synthesizers + streamingSynthesizers + recognizers + streamingRecognizers + connectionProbes)
            .firstOrNull { serviceId in it.serviceIds }
            ?.endpointStrategy
            ?: EndpointStrategy.ConfiguredBaseUrl

    override fun isSupported(serviceId: String, capability: ModelCapability): Boolean = when (capability) {
        ModelCapability.TTS -> synthesizers.any { serviceId in it.serviceIds } ||
            streamingSynthesizers.any { serviceId in it.serviceIds }
        ModelCapability.STT -> recognizers.any { serviceId in it.serviceIds && ModelCapability.STT in it.capabilities }
        ModelCapability.ASR -> recognizers.any { serviceId in it.serviceIds && ModelCapability.ASR in it.capabilities }
        ModelCapability.LLM -> true
        // 原生实时会话由 Realtime 适配器自述接入情况；未接入的服务不得选它的 Realtime 模型。
        ModelCapability.Realtime -> realtimeSupports.any { it.serviceId == serviceId }
    }

    /**
     * 该服务的原生 Realtime 适配器声明；未接入时返回 null。
     */
    override fun realtimeSupport(serviceId: String): RealtimeVoiceSupport? =
        realtimeSupports.firstOrNull { it.serviceId == serviceId }
}
