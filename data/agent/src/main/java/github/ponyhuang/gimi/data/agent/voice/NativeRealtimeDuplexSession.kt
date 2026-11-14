@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import android.util.Log
import com.google.adk.kt.models.LiveConnection
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.RealtimeInput
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Tool
import github.ponyhuang.gimi.core.audio.Pcm16Resampler
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceDuplexSession
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceEvent
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import github.ponyhuang.gimi.domain.voice.repository.VoiceHandshakeException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import github.ponyhuang.gimi.data.modelaccess.VoiceAudioMarkers
import github.ponyhuang.gimi.data.modelaccess.VoiceLiveModelFactory
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeFunctionCallSink

/**
 * Realtime 模式的全双工会话门面（厂商无关）。
 *
 * 内部经 [VoiceLiveModelFactory] 取得厂商原生模型（[OpenAiRealtimeModel] /
 * [QwenRealtimeModel]，ADK Model）并持有其 [LiveConnection]，负责连接生命周期：
 * 会话配置在模型层下发；本类负责断线退避重连（重连即新会话、上下文不重放）与
 * 鉴权失败即终止。对编排层只暴露领域事件与 16 kHz 音频上行——重采样与否由
 * [uplinkProvider] 契约决定：OpenAI 需升采样到 24 kHz（相位跨重连持续，采集链
 * 不受影响），Qwen 原生 16 kHz 直喂。契约在首个事件/首帧上行时惰性解析一次
 * （门面构造非挂起，而目录等待是挂起操作），重复解析无害。
 *
 * 实时助手模式的工具执行由 [tools] 承载：工具声明随会话配置下发，模型发起的
 * function_call 经 [RealtimeToolRoundRunner] 在门面内执行（不阻塞事件接收），
 * 结果经连接的 [RealtimeFunctionCallSink] 写回并触发二轮推理；断线时执行中的
 * 轮次随 coroutineScope 取消——重连即新会话，工具轮不跨连接续跑。
 *
 * 断线期间上行进入小缓冲（满则丢最旧），重连成功后一次冲出；发送失败静默转缓冲，
 * 不向编排层暴露瞬时抖动——会话不可恢复时以 [RealtimeVoiceEvent.Failed] 收尾。
 */
internal class NativeRealtimeDuplexSession(
    private val models: VoiceLiveModelFactory,
    private val selection: ModelSelection?,
    private val instructions: String,
    private val uplinkProvider: suspend () -> RealtimeUplink,
    /** 会话音色的自定义覆盖；null 表示使用所选模型在该厂商下的缺省音色。 */
    private val voice: String? = null,
    /** 实时助手的工具桥；null 表示实时轻聊（无工具）。 */
    private val tools: RealtimeVoiceToolBridge? = null,
) : RealtimeVoiceDuplexSession {

    @Volatile private var uplink: RealtimeUplink? = null
    @Volatile private var resampler: Pcm16Resampler? = null
    private val lock = Any()
    private var connection: LiveConnection? = null

    /** 重连等待期与发送失败瞬间的上行缓冲；消费方不感知断线。 */
    private val pendingAudio = ArrayDeque<ByteArray>()
    private var pendingBytes = 0

    @Volatile private var closed = false

    override fun events(): Flow<RealtimeVoiceEvent> = flow {
        resolveUplink()
        var attempt = 0
        var reconnected = false
        while (true) {
            try {
                val connection = connectOnce()
                synchronized(lock) { this@NativeRealtimeDuplexSession.connection = connection }
                emit(RealtimeVoiceEvent.Ready(reconnected))
                reconnected = true
                flushPendingAudio(connection)
                // coroutineScope 把工具执行轮挂到本连接上：事件流终止（断线）即取消，
                // 重连后的新会话不再接收上一连接遗留的工具轮结果。
                coroutineScope {
                    // 工具桥可为空：仍要构造执行轮，把模型意外发起的 function_call 回写成
                    // 失败结果并触发 response.create，避免服务端不收尾、界面卡在「正在生成」。
                    val rounds = RealtimeToolRoundRunner(tools, connection as? RealtimeFunctionCallSink)
                    val scope = this
                    connection.receive().collect { response -> translate(scope, response, rounds) }
                }
                // 事件流正常结束意味着服务端关闭了会话；按传输失败处理并重连。
                throw IOException("Realtime 会话被服务端关闭")
            } catch (e: VoiceHandshakeException) {
                // 密钥无效/权限不足重试无意义：直接进入失败态。
                Log.w(TAG, "Realtime 握手失败，不重试", e)
                emit(RealtimeVoiceEvent.Failed(RealtimeVoiceFailure.Configuration))
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: VoiceAudioBackendException) {
                Log.w(TAG, "Realtime 会话失败，不重试", e)
                emit(RealtimeVoiceEvent.Failed(e.failure))
                return@flow
            } catch (e: Exception) {
                if (closed) return@flow
                attempt++
                if (attempt > MAX_RECONNECT_ATTEMPTS) {
                    Log.w(TAG, "Realtime 重连 $MAX_RECONNECT_ATTEMPTS 次仍失败，放弃", e)
                    emit(RealtimeVoiceEvent.Failed(RealtimeVoiceFailure.Generation))
                    return@flow
                }
                Log.w(TAG, "Realtime 连接断开，第 $attempt 次重连前退避", e)
                clearConnection()
                delay(backoffMs(attempt))
            }
        }
    }

    /** 惰性解析上行契约；重复调用无害（幂等），调用方随后即可读 [uplink] 快照。 */
    private suspend fun resolveUplink(): RealtimeUplink {
        uplink?.let { return it }
        val resolved = uplinkProvider()
        uplink = resolved
        resampler = if (resolved.resampled) Pcm16Resampler(INPUT_RATE_HZ, resolved.sampleRateHz) else null
        return resolved
    }

    private suspend fun uplinkMimeType(): String =
        "audio/pcm;rate=${resolveUplink().sampleRateHz}"

    override suspend fun sendAudio(pcm16: ByteArray) {
        // 按厂商上行契约整形：OpenAI 重采样 16k→24k，Qwen 原生 16k 直喂。
        resolveUplink()
        val payload = resampler?.process(pcm16) ?: pcm16
        if (payload.isEmpty()) return
        val active = synchronized(lock) { connection }
        if (active != null) {
            try {
                active.sendRealtime(RealtimeInput.Audio(Blob(mimeType = uplinkMimeType(), data = payload)))
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Realtime 上行发送失败，转入重连缓冲", e)
            }
        }
        synchronized(lock) {
            this.connection = null
            bufferAudio(payload)
        }
    }

    override suspend fun close() {
        closed = true
        val current = synchronized(lock) {
            val current = connection
            connection = null
            pendingAudio.clear()
            pendingBytes = 0
            current
        }
        runCatching { current?.closeSession() }
        // 桥自建资源（应用函数工具集等）随会话释放；共享单例资源不在其列。
        runCatching { tools?.close() }
    }

    private suspend fun connectOnce(): LiveConnection {
        val model = models.create(selection, voice)
        val declarations = tools?.declarations.orEmpty()
        return model.connect(
            LlmRequest(
                model = model,
                config = GenerateContentConfig(
                    systemInstruction = Content.fromText(Role.SYSTEM, instructions),
                    tools = declarations.takeUnless { it.isEmpty() }?.let { listOf(Tool(functionDeclarations = it)) },
                ),
            ),
        )
    }

    private suspend fun FlowCollector<RealtimeVoiceEvent>.translate(
        scope: kotlinx.coroutines.CoroutineScope,
        response: LlmResponse,
        rounds: RealtimeToolRoundRunner,
    ) {
        if (response.errorCode != null || !response.errorMessage.isNullOrBlank()) {
            Log.w(TAG, "Realtime 协议错误: ${response.errorCode} ${response.errorMessage}")
            emit(RealtimeVoiceEvent.Failed(RealtimeVoiceFailure.Generation))
            return
        }
        if (response.interrupted) emit(RealtimeVoiceEvent.Interrupted)
        response.inputTranscription?.text?.takeIf { it.isNotEmpty() }?.let {
            emit(RealtimeVoiceEvent.UserTranscript(it))
        }
        response.outputTranscription?.text?.takeIf { it.isNotEmpty() }?.let {
            emit(RealtimeVoiceEvent.AssistantTranscriptDelta(it))
        }
        for (part in response.content?.parts.orEmpty().filter { it.thought != true }) {
            val audio = part.inlineData ?: continue
            if (audio.mimeType != "audio/pcm;rate=24000") continue
            emit(RealtimeVoiceEvent.AssistantAudio(checkNotNull(audio.data)))
        }
        // function_call part 定稿即执行：通知编排层（界面提示），执行轮放行到子协程，
        // 不阻塞事件接收（执行期间服务端仍可能推送打断/转写事件）。
        val calls = response.content?.parts.orEmpty().filter { it.thought != true }.mapNotNull { it.functionCall }
        if (calls.isNotEmpty()) {
            for (call in calls) emit(RealtimeVoiceEvent.ToolCallStarted(call.name))
            // 执行轮不阻塞事件接收；无桥时由执行轮回写失败结果并照样触发二轮推理。
            scope.launch { rounds.run(calls) }
        }
        if (response.customMetadata?.get(VoiceAudioMarkers.TURN_COMMITTED) == true) {
            emit(RealtimeVoiceEvent.TurnCommitted)
        }
        if (response.turnComplete == true) emit(RealtimeVoiceEvent.TurnCompleted)
    }

    private fun bufferAudio(bytes: ByteArray) {
        pendingAudio.addLast(bytes)
        pendingBytes += bytes.size
        while (pendingBytes > MAX_PENDING_AUDIO_BYTES) {
            pendingAudio.removeFirstOrNull()?.let { pendingBytes -= it.size }
        }
    }

    private suspend fun flushPendingAudio(connection: LiveConnection) {
        val buffered = synchronized(lock) {
            val buffered = pendingAudio.toList()
            pendingAudio.clear()
            pendingBytes = 0
            buffered
        }
        for (bytes in buffered) {
            try {
                connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = uplinkMimeType(), data = bytes)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "重连后冲刷上行缓冲失败，丢弃剩余帧", e)
                return
            }
        }
    }

    private suspend fun clearConnection() {
        val stale = synchronized(lock) {
            val stale = connection
            connection = null
            stale
        }
        // 挂起调用不得留在临界区内；关闭动作放到锁外。
        runCatching { stale?.closeSession() }
    }

    private fun backoffMs(attempt: Int): Long = minOf(1000L shl (attempt - 1), MAX_BACKOFF_MS)

    private companion object {
        const val TAG = "NativeRealtimeDuplex"
        const val INPUT_RATE_HZ = 16_000

        /** 有限退避：最多 5 次重连（1s/2s/4s/8s/8s），耗尽进入失败态。 */
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val MAX_BACKOFF_MS = 8_000L

        /** 断线期间的上行缓冲上限；约 5 秒 24 kHz PCM16，超出丢最旧。 */
        const val MAX_PENDING_AUDIO_BYTES = 256 * 1024
    }
}
