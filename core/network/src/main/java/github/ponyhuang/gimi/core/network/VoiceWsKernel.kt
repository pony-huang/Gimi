package github.ponyhuang.gimi.core.network

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.time.Duration.Companion.milliseconds

/**
 * 语音服务的 WebSocket 传输内核。
 *
 * 只负责帧的收发与连接状态，不解释任何厂商协议：控制事件统一是 JSON 文本帧，
 * 音频视厂商而定走二进制帧还是 JSON 里的十六进制负载。协议解释留给各能力适配器，
 * 因此百炼的 `header`/`payload` 结构、MiniMax 的扁平事件名与 OpenAI Realtime 的事件流
 * 可以共用同一段传输代码。
 *
 * 握手失败的业务分类由调用方注入：内核只知道 HTTP 状态码，不知道「密钥无效」
 * 在具体领域里属于哪一类失败。
 */
class VoiceWsKernel(
    client: OkHttpClient,
    request: Request,
    private val handshakeFailure: (statusCode: Int, cause: Throwable, detail: String?) -> Throwable,
) {
    // 无界通道：音频消费方（播放器）背压变慢时帧会在内存积压（24 kHz PCM 约 48 KB/s）。
    // 有界+丢弃会静默丢音频帧，比积压更难排查，故保持无界并依赖调用方及时消费。
    private val frames = Channel<WsFrame>(Channel.UNLIMITED)
    private val socket: WebSocket = client.newWebSocket(request, Listener())

    /** 事件通道；调用方消费完后直接退出循环即可，关闭会话会一并结束通道。 */
    fun frames(): ReceiveChannel<WsFrame> = frames

    /** 发送一个控制事件；连接已断开抛 [IOException]，让失败映射归入网络类而非 Unknown。 */
    suspend fun sendControl(payload: JsonObject) {
        if (!socket.send(payload.toString())) throw IOException("语音服务连接已断开")
    }

    /** 发送一段二进制音频。 */
    suspend fun sendAudio(bytes: ByteArray) {
        // OkHttp 的发送队列有上限；接近上限时先等一会儿，避免直接丢帧。
        while (socket.queueSize() > MAX_PENDING_AUDIO_BYTES) delay(SEND_BACKPRESSURE_DELAY_MS.milliseconds)
        if (!socket.send(bytes.toByteString())) throw IOException("语音服务连接已断开")
    }

    /**
     * 关闭会话。
     *
     * 直接断开连接而不是只发关闭帧：按连接时长计费的语音服务会让半开的连接继续计费，
     * 断开连接不会重放任何已发送的音频。
     */
    fun close() {
        socket.cancel()
    }

    /** 收到的一帧。 */
    sealed interface WsFrame {
        /** 握手成功；会话已可用于发送控制事件。 */
        data object Opened : WsFrame

        /** 控制事件（JSON 文本帧）。 */
        class Control(val payload: JsonObject) : WsFrame

        /** 二进制音频帧。 */
        class Audio(val bytes: ByteArray) : WsFrame

        /** 连接或传输失败。 */
        class Failure(val error: Throwable) : WsFrame

        /** 会话结束。 */
        data object Closed : WsFrame
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            frames.trySend(WsFrame.Opened)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val payload = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
            frames.trySend(WsFrame.Control(payload))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            frames.trySend(WsFrame.Audio(bytes.toByteArray()))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val code = response?.code
            if (code != null) {
                val detail = handshakeDetail(response)
                Log.w(TAG, "语音服务握手被拒（HTTP $code）$detail", t)
                frames.trySend(WsFrame.Failure(handshakeFailure(code, t, detail)))
            } else {
                frames.trySend(WsFrame.Failure(t))
            }
            frames.close()
        }

        /**
         * 握手被拒时服务端返回的原始详情：响应头（脱敏）与响应体（截断）。
         * 厂商的错误码与 request-id 通常只在这里出现，是向服务方反馈问题的凭据。
         */
        private fun handshakeDetail(response: Response): String {
            val headers = response.headers.names()
                .filter { it.lowercase() in LOGGED_RESPONSE_HEADERS }
                .joinToString(", ") { "${it}: ${response.header(it)}" }
                .ifEmpty { "-" }
            val body = runCatching { response.peekBody(MAX_HANDSHAKE_BODY_BYTES).string().trim() }
                .getOrNull()?.takeIf { it.isNotEmpty() } ?: "-"
            return "headers=[$headers], body=$body"
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            frames.trySend(WsFrame.Closed)
            frames.close()
        }
    }

    private companion object {
        const val TAG = "VoiceWsKernel"
        const val MAX_PENDING_AUDIO_BYTES = 1L * 1024L * 1024L
        const val SEND_BACKPRESSURE_DELAY_MS = 10L

        /** 握手失败详情的响应体截断上限；厂商错误 JSON 通常远小于此。 */
        const val MAX_HANDSHAKE_BODY_BYTES = 8L * 1024L

        /** 只记录这几类响应头：够定位问题（request-id、限流信息），又不引入敏感回显。 */
        val LOGGED_RESPONSE_HEADERS = setOf("x-request-id", "request-id", "content-type", "server", "date", "www-authenticate")
    }
}
