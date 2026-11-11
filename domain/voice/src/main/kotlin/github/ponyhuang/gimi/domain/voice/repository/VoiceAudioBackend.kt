package github.ponyhuang.gimi.domain.voice.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 创建独立的音频推理会话；不拥有麦克风、播放设备或 Android 生命周期。 */
interface VoiceAudioBackend {
    fun open(configuration: RealtimeVoiceConfiguration = RealtimeVoiceConfiguration()): VoiceAudioBackendSession

    /**
     * 打开 Realtime 引擎的全双工会话；仅 mode 满足 [RealtimeVoiceMode.isRealtimeEngine] 时支持。
     *
     * 与 [open] 的分轮契约不同，这里返回常驻双工会话：连接生命周期（配置下发、
     * 断线退避重连、重连即新会话）由实现内部负责，编排层只消费 [RealtimeVoiceEvent]
     * 并按采集节奏上行音频。挂起语义用于会话开启前解析工具桥（实时助手模式，
     * 可能涉及 MCP 连接探测），调用方在协程中调用即可。
     */
    suspend fun openDuplex(configuration: RealtimeVoiceConfiguration): RealtimeVoiceDuplexSession
}

/** 原生实时语音（Realtime 模式）服务端事件的领域词汇；厂商细节由数据层翻译。 */
sealed interface RealtimeVoiceEvent {
    /**
     * 会话就绪：会话配置已被服务端确认，可以开始上行音频。
     * @property reconnected 是否为断线重连后的就绪；编排层据此提示「已重新连接」。
     */
    data class Ready(val reconnected: Boolean) : RealtimeVoiceEvent

    /** 用户语音的服务端转写（整句），供用户侧字幕。 */
    data class UserTranscript(val text: String) : RealtimeVoiceEvent

    /** 用户输入已提交，模型开始生成响应。 */
    data object TurnCommitted : RealtimeVoiceEvent

    /** 助手回复音频块（24 kHz 单声道 PCM16 小端）。 */
    class AssistantAudio(val pcm24k: ByteArray) : RealtimeVoiceEvent

    /** 助手回复转写增量，供助手侧字幕。 */
    data class AssistantTranscriptDelta(val text: String) : RealtimeVoiceEvent

    /**
     * 模型发起一次工具调用，客户端即将执行并把结果写回服务端。
     * @property toolName 工具声明名（协议层 function_call 的 name），供界面提示。
     */
    data class ToolCallStarted(val toolName: String) : RealtimeVoiceEvent

    /** 服务端因用户说话取消当前响应（协议级打断）。 */
    data object Interrupted : RealtimeVoiceEvent

    /** 一条响应结束（completed 或 cancelled）；编排层据此排空播放并回到监听。 */
    data object TurnCompleted : RealtimeVoiceEvent

    /** 会话不可恢复地失败（重连次数耗尽或鉴权失败）；编排层进入失败态。 */
    data class Failed(val failure: RealtimeVoiceFailure) : RealtimeVoiceEvent
}

/**
 * 原生实时语音的全双工会话门面。
 *
 * 与级联的 [VoiceAudioBackendSession]（分轮 transcribe/respond）不同，这是一条
 * 常驻会话：上行 [sendAudio] 按采集节奏持续投喂（静音与播放期门控由编排层决定），
 * 下行 [events] 持续产出服务端事件。断线重连后上下文不重放（新会话）。
 */
interface RealtimeVoiceDuplexSession {
    /** 服务端事件流；单消费者，随会话关闭而结束。 */
    fun events(): Flow<RealtimeVoiceEvent>

    /** 上行一帧 16 kHz 单声道 PCM16 采集音频；实现内部重采样为 24 kHz。 */
    suspend fun sendAudio(pcm16: ByteArray)

    suspend fun close()
}

/**
 * 分轮音频后端。输入为 16 kHz 单声道 PCM16 小端音频，输出为 24 kHz 同格式音频。
 * 识别与回答分开，以便新输入识别有效后才取消旧回答；空识别结果不得触发回答。
 * 调用方负责串行收集回答、取消过期操作，并在取消任务后关闭会话。
 * 这是应用当前的分轮交互契约；供应商适配统一使用数据层的官方 ADK Model/LiveConnection。
 * 回答和音频需要并行消费，音频未被消费时回答流可因背压挂起，取消回答必须同时取消音频消费。
 */
interface VoiceAudioBackendSession {
    suspend fun isAvailable(): Boolean

    /**
     * 流式转写：产出中间结果与最终结果。
     *
     * 中间结果用于让界面显示"正在听到……"；只有最终结果才是本轮输入。
     * 服务未接入流式识别时只产出最终结果，因此调用方可以统一按流消费。
     */
    fun transcribe(pcm16: ByteArray): Flow<RecognitionEvent>

    /** previousSpokenText 只包含上一轮实际完整播完的文本；取消收集必须停止生成。 */
    fun respond(text: String, previousSpokenText: String): Flow<VoiceAudioEvent>
    /** 只在硬件队列完整播完后确认，稳定片段 ID 使重复确认无副作用。 */
    fun confirmPlayback(segmentId: String, text: String)
    /** 生成与播放均结束或取消后提交本轮文字；失败保留原事件 ID，供显式重试。 */
    suspend fun finishTurn()
    suspend fun retryPersistence()
    suspend fun close()
}

/** 后端输出；文本先供展示，音频按事件顺序消费，播放完成后才能确认已听文本。 */
sealed interface VoiceAudioEvent {
    /** 模型确认当前回答已被打断，消费方必须停止并清空该回答的播放队列。 */
    data object Interrupted : VoiceAudioEvent

    /** 本轮模型正文增量，text 不包含思考或重复聚合内容。 */
    data class Text(val text: String) : VoiceAudioEvent

    /**
     * 一个待播放语音片段；audio 为惰性、单次收集的 PCM 流。
     * @property text 与本片段对应的转写，允许晚于音频到达；完整播完后读取快照加入已听上下文。
     * @property audio 网络分块不保证采样边界；调用方负责背压及取消播放。
     * @property id 片段的稳定身份，用于对完整播放回执去重。
     */
    data class Audio(val text: StateFlow<String>, val audio: Flow<ByteArray>, val id: String) : VoiceAudioEvent
}

/** 后端失败分类；取消异常必须原样传播，不映射为业务失败。 */
class VoiceAudioBackendException(
    val failure: RealtimeVoiceFailure,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
