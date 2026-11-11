package github.ponyhuang.gimi.domain.voice.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 会话式语音合成的开窗参数。
 *
 * 与 [SynthesisRequest] 的区别是合成文本在会话内逐次投递，开窗时没有待合成的文本。
 *
 * @property voiceId 音色 ID；会话内固定，服务端在开窗事件里锁定。
 * @property modelId 合成模型 ID；会话内固定。
 * @property apiKey 服务访问密钥。
 * @property baseUrl 服务基址；端点由厂商自有时该值不参与请求构造。
 * @property workspaceId 业务空间 ID（百炼）；端点由厂商自有的服务据此构造专属域名，其余厂商忽略。
 */
data class SynthesisSessionRequest(
    val voiceId: String,
    val modelId: String,
    val apiKey: String,
    val baseUrl: String,
    val workspaceId: String? = null,
)

/**
 * 会话产出的一段语音。
 *
 * @property text 本段文本的快照；服务端可能晚于音频给出，调用方按需读取。
 * @property audio 本段的音频分块；调用方负责背压与取消。
 */
data class SynthesisSegment(
    val text: StateFlow<String>,
    val audio: Flow<ByteArray>,
)

/**
 * 一次会话式语音合成。
 *
 * 与一次性合成（[SpeechSynthesizer.synthesize]）的差别有三点：
 * - 文本在会话内逐次投递，服务端可以在只收到半句话时就开始合成，首包不必等整句；
 * - 音频随服务端合成进度下发，调用方可以边收边播；
 * - [cancel] 让服务端真正停止合成并丢弃尚未合成的文本，
 *   而不是调用方单方面断开连接、让服务端继续合成并计费。
 */
interface SpeechSynthesisSession {
    /** 追加待合成文本；任意粒度均可，服务端按标点自行切句。 */
    fun send(text: String)

    /**
     * 强制服务端合成并下发当前缓冲的文本。
     *
     * 返回时该部分音频已全部下发，因此调用方可以在此之后结束本轮。
     */
    suspend fun flush()

    /**
     * 取消当前合成。
     *
     * 服务端丢弃尚未合成的文本并停止下发音频；已经下发的音频无法撤回。
     * 会话在取消后仍可继续投递文本。
     */
    fun cancel()

    /**
     * 本会话产出的语音片段；片段边界由服务端给出。
     *
     * 流在 [flush] 返回后结束：此后服务端不会再为本轮产出新的片段，
     * 调用方因此可以在片段流结束后认为本轮音频已经完整。
     */
    val segments: Flow<SynthesisSegment>

    /** 结束会话并释放连接。 */
    suspend fun close()
}

/**
 * 会话式语音合成能力。
 *
 * 不支持会话式合成的厂商无需实现本接口："该厂商不支持会话式合成"因此是类型层面的事实，
 * 调用方据此回退到一次性合成，而不是在运行中意外降级。
 */
interface StreamingSpeechSynthesizer : CapabilityAdapter {
    /** 打开一次合成会话。 */
    fun open(request: SynthesisSessionRequest): SpeechSynthesisSession
}
