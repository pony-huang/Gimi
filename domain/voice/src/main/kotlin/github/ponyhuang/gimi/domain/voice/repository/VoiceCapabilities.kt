package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.model.SpeechContextMessage
import kotlinx.coroutines.flow.Flow

/**
 * 语音能力适配器的公共身份。
 *
 * 适配器按**服务标识**与**能力**注册：同一个适配器可以服务多个服务，同一个厂商的多个能力
 * 也可以共用一份协议实现。解析时按 `(serviceId, 能力)` 定位适配器；定位不到即为
 * "该服务未接入此能力"，调用方据此给出类型化失败，而不是改用其它协议的兜底实现。
 */
interface CapabilityAdapter {
    /** 本适配器可服务的服务标识集合。 */
    val serviceIds: Set<String>

    /** 本适配器可承担的能力集合。 */
    val capabilities: Set<ModelCapability>

    /**
     * 本适配器的端点来源策略。
     *
     * 默认按用户配置的基址构造请求；厂商自有端点的适配器必须显式覆盖。
     */
    val endpointStrategy: EndpointStrategy
        get() = EndpointStrategy.ConfiguredBaseUrl
}

/**
 * 语音合成请求。
 *
 * @property text 待朗读的文本。
 * @property voiceId 音色 ID；由调用方从音色列表中选定。
 * @property modelId 合成模型 ID。
 * @property apiKey 服务访问密钥。
 * @property baseUrl 服务基址；端点由厂商自有时该值不参与请求构造。
 * @property workspaceId 业务空间 ID（百炼）；端点由厂商自有的服务据此构造专属域名，其余厂商忽略。
 */
data class SynthesisRequest(
    val text: String,
    val voiceId: String,
    val modelId: String,
    val apiKey: String,
    val baseUrl: String,
    val workspaceId: String? = null,
)

/**
 * 语音识别请求。
 *
 * @property pcm16 单声道 PCM16 音频。
 * @property modelId 识别模型 ID。
 * @property apiKey 服务访问密钥。
 * @property baseUrl 服务基址；端点由厂商自有时该值不参与请求构造。
 * @property sampleRateHz 音频采样率。
 * @property channelCount 声道数。
 * @property language 识别语种；`auto` 表示由服务自行判定。
 * @property context 可选的对话上下文，用于提升专有名词识别准确率；为空表示不携带上下文。
 * @property workspaceId 业务空间 ID（百炼）；端点由厂商自有的服务据此构造专属域名，其余厂商忽略。
 */
data class RecognitionRequest(
    val pcm16: ByteArray,
    val modelId: String,
    val apiKey: String,
    val baseUrl: String,
    val sampleRateHz: Int = 16_000,
    val channelCount: Int = 1,
    val language: String = "auto",
    val context: List<SpeechContextMessage> = emptyList(),
    val workspaceId: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecognitionRequest) return false
        return pcm16.contentEquals(other.pcm16) &&
            modelId == other.modelId &&
            apiKey == other.apiKey &&
            baseUrl == other.baseUrl &&
            sampleRateHz == other.sampleRateHz &&
            channelCount == other.channelCount &&
            language == other.language &&
            context == other.context &&
            workspaceId == other.workspaceId
    }

    override fun hashCode(): Int {
        var result = pcm16.contentHashCode()
        result = 31 * result + modelId.hashCode()
        result = 31 * result + apiKey.hashCode()
        result = 31 * result + baseUrl.hashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + channelCount
        result = 31 * result + language.hashCode()
        result = 31 * result + context.hashCode()
        result = 31 * result + (workspaceId?.hashCode() ?: 0)
        return result
    }
}

/**
 * 语音合成能力。
 *
 * 输出为音频分块流：合成方在服务端产出音频的同时下发分块，调用方可边接收边播放；
 * 需要完整音频的调用方自行收集全部分块。
 */
interface SpeechSynthesizer : CapabilityAdapter {
    fun synthesize(request: SynthesisRequest): Flow<ByteArray>
}

/**
 * 一次性语音识别能力：返回该段音频的最终文本。
 *
 * 同一接口同时服务 [ModelCapability.STT]（专用转写服务）与 [ModelCapability.ASR]
 * （音频理解模型）两种语义；两者的区别在于模型自身的属性与厂商协议，
 * 不在于识别这一步的调用形态。
 */
interface SpeechRecognizer : CapabilityAdapter {
    suspend fun recognize(request: RecognitionRequest): String
}

/**
 * 流式语音识别能力。
 *
 * 产出中间结果与最终结果两类事件；最终结果出现后流结束。
 */
/**
 * 流式语音识别能力。
 *
 * 产出中间结果与最终结果两类事件；最终结果出现后流结束。不支持流式的厂商无需实现本接口，
 * "该厂商不支持流式识别"因此是类型层面的事实，而不是运行时的意外。
 */
interface StreamingSpeechRecognizer : CapabilityAdapter {
    fun recognize(request: RecognitionRequest): Flow<RecognitionEvent>
}

/** 识别事件。 */
sealed interface RecognitionEvent {
    /** 中间识别结果；后续仍会有更新或最终结果到来。 */
    data class Partial(val text: String) : RecognitionEvent

    /** 最终识别结果；该段音频的识别到此结束。 */
    data class Final(val text: String) : RecognitionEvent
}

/**
 * 语音能力适配器的解析入口。
 *
 * 界面据此把"服务未接入某能力"的模型显示为不可选，而不是静默隐藏，避免用户误判为配置失败；
 * 连接测试据此选用该服务自己的协议，而不是别的厂商的协议。
 */
interface VoiceCapabilityResolver {
    /** 该服务是否已接入指定能力。 */
    fun isSupported(serviceId: String, capability: ModelCapability): Boolean

    /**
     * 解析该服务的连通性探测能力。
     *
     * @throws VoiceCapabilityNotSupported 该服务未实现连接测试。
     */
    fun connectionProbe(serviceId: String): SpeechConnectionProbe

    /**
     * 该服务的端点来源策略。
     *
     * 设置界面据此决定是否展示"接入地址"字段：端点由厂商自有的服务不应让用户填写
     * 一个不生效的地址。
     */
    fun endpointStrategy(serviceId: String): EndpointStrategy

    /**
     * 该服务的原生全双工实时语音适配器声明。
     *
     * null 表示该服务没有实现常驻双工会话；此时该服务下的 Realtime 标记模型不得被选为
     * Realtime 默认模型（它们会静默回落到级联链路，而级联链路要求 STT/TTS 能力）。
     */
    fun realtimeSupport(serviceId: String): RealtimeVoiceSupport?
}

/**
 * 目标服务未接入所请求能力。
 *
 * 与"配置缺失"和"调用失败"区分开：调用方据此提示用户该厂商不支持此能力，
 * 而不是重试或改用其它协议的兜底实现。
 *
 * @property serviceId 未接入该能力的服务标识。
 * @property capability 被请求的能力。
 */
class VoiceCapabilityNotSupported(
    val serviceId: String,
    val capability: ModelCapability,
) : IllegalStateException(
    "服务 $serviceId 未接入能力 $capability",
)

/**
 * 端点来源策略。
 *
 * 语音服务的接入地址与聊天服务的基址不是同一类地址：有的厂商允许用户自定义（自建代理、
 * 私有部署），有的厂商端点由厂商自有、用户填写的地址不参与请求构造。适配器显式声明自己的
 * 策略，设置界面据此决定是否展示"接入地址"字段，避免用户填写一个不生效的地址。
 */
sealed interface EndpointStrategy {
    /** 端点来自用户配置的基址；请求使用该地址构造。 */
    data object ConfiguredBaseUrl : EndpointStrategy

    /** 端点由厂商自有；用户配置的基址不参与请求构造。 */
    data object VendorOwned : EndpointStrategy
}
