@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.modelaccess

import com.google.adk.kt.models.ContentInput
import com.google.adk.kt.models.LiveConnection
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.RealtimeInput
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Modality
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Transcription
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RecognitionEvent
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import github.ponyhuang.gimi.data.modelaccess.VoiceSentenceBuffer
import github.ponyhuang.gimi.data.modelaccess.VoiceAudioMarkers

/**
 * 将现有 ASR、文字模型和 TTS 实现为 ADK 官方 Model/LiveConnection。
 * 文字模型不支持原生 Live 也可使用；音频输入只接受显式活动边界，不伪装服务端自动 VAD。
 * 连接不拥有录音、播放器、工具或持久化历史，可被官方 Runner 或应用的已听上下文执行层消费。
 */
class CascadedVoiceModel(
    private val llmModel: Model,
    private val recognition: SpeechRecognitionRepository,
    private val synthesis: SpeechSynthesisRepository,
) : Model {
    override val name: String = "cascaded:${llmModel.name}"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        llmModel.generateContent(request.copy(model = llmModel), stream)

    override suspend fun connect(request: LlmRequest): LiveConnection {
        val live = request.liveConnectConfig
        require(live.responseModalities.isNullOrEmpty() || live.responseModalities == listOf(Modality.AUDIO)) {
            "Cascaded voice requires AUDIO output"
        }
        require(live.tools.isNullOrEmpty() && request.config.tools.isNullOrEmpty()) { "Cascaded voice has no tools" }
        require(live.realtimeInputConfig?.activityHandling == null && live.realtimeInputConfig?.turnCoverage == null) {
            "Cascaded voice does not support custom activity handling or turn coverage"
        }
        require(live.sessionResumption == null && live.contextWindowCompression == null &&
            live.translationConfig == null && live.proactivity == null && live.enableAffectiveDialog != true &&
            live.explicitVadSignal != true && live.speechConfig == null) {
            "Unsupported cascaded voice configuration; voice selection belongs to speech settings"
        }
        return Connection(request)
    }

    private inner class Connection(private val request: LlmRequest) : LiveConnection {
        private val lock = Any()
        private val collecting = Mutex()
        private val turns = Channel<Turn>(1)
        private val history = mutableListOf<Content>()
        private val textInput = StringBuilder()
        private val audioInput = ByteArrayOutputStream()
        private var historySent = false
        private var inputSent = false
        private var recording = false
        private var closed = false
        private var active: Job? = null

        override suspend fun sendHistory(history: List<Content>) {
            val seeded = history.map { content ->
                content.copy(parts = content.parts.filter { it.text != null && it.thought != true })
            }.filter { it.parts.isNotEmpty() }
            synchronized(lock) {
                check(!closed && !historySent && !inputSent) { "History must be sent once before input" }
                historySent = true
                this.history.addAll(if (seeded.lastOrNull()?.role == Role.USER) seeded.dropLast(1) else seeded)
            }
            seeded.lastOrNull()?.takeIf { it.role == Role.USER }?.let { sendContent(it) }
        }

        override suspend fun sendContent(content: Content, partial: Boolean) {
            // 构造结果被有意丢弃：ADK 的 ContentInput 构造自带 parts 非空校验，
            // 这里借它提前拒绝空 parts 的文本轮；删掉会让空轮静默变成一次空请求。
            ContentInput(content, partial)
            require(content.role == Role.USER && content.parts.all { it.text != null }) {
                "Cascaded voice accepts user text, not tools or other media"
            }
            val turn = synchronized(lock) {
                check(!closed)
                check(!recording) { "Cannot mix a text turn with an open audio activity" }
                inputSent = true
                require(textInput.length + content.parts.sumOf { it.text.orEmpty().length } <= MAX_INPUT_BYTES) {
                    "Partial text exceeds the voice input limit"
                }
                textInput.append(content.parts.joinToString("") { it.text.orEmpty() })
                if (partial) null else Turn.Text(textInput.toString().also { textInput.clear() })
            }
            if (turn != null) {
                interrupt()
                turns.send(turn)
            }
        }

        override suspend fun sendRealtime(input: RealtimeInput) {
            require(request.liveConnectConfig.realtimeInputConfig?.automaticActivityDetection?.disabled == true) {
                "Cascaded audio requires manual activity detection"
            }
            val turn = synchronized(lock) {
                check(!closed)
                check(textInput.isEmpty()) { "Finish the partial text turn before sending audio" }
                inputSent = true
                when (input) {
                    RealtimeInput.ActivityStart -> {
                        audioInput.reset()
                        recording = true
                        null
                    }
                    is RealtimeInput.Audio -> {
                        check(recording) { "Send ActivityStart before audio" }
                        require(input.blob.mimeType == "audio/pcm;rate=16000") { "Expected mono PCM16 at 16 kHz" }
                        val bytes = input.blob.data ?: byteArrayOf()
                        require(audioInput.size() + bytes.size <= MAX_INPUT_BYTES) { "Voice activity exceeds 30 seconds" }
                        audioInput.write(bytes)
                        null
                    }
                    RealtimeInput.ActivityEnd -> {
                        check(recording) { "No audio activity is open" }
                        recording = false
                        val bytes = audioInput.toByteArray()
                        audioInput.reset()
                        require(bytes.size % 2 == 0) { "Incomplete PCM16 input" }
                        if (bytes.isEmpty()) null else Turn.Audio(bytes)
                    }
                    RealtimeInput.AudioStreamEnd -> error("Use ActivityEnd when automatic VAD is disabled")
                    is RealtimeInput.Video -> throw UnsupportedOperationException("Cascaded voice does not accept video")
                }
            }
            if (input == RealtimeInput.ActivityStart) interrupt()
            if (turn != null) turns.send(turn)
        }

        private fun interrupt() {
            synchronized(lock) { active?.cancel(TurnInterrupted()) }
        }

        override fun receive(): Flow<LlmResponse> = flow {
            check(collecting.tryLock()) { "Only one live receiver is allowed" }
            try {
                val turn = turns.receiveCatching().getOrNull() ?: return@flow
                coroutineScope {
                    val responses = Channel<Delivery>(Channel.RENDEZVOUS)
                    val worker = async(start = CoroutineStart.LAZY) {
                        try {
                            respond(turn) { response ->
                                val delivery = Delivery(response, CompletableDeferred())
                                responses.send(delivery)
                                // emit 返回才表示消费方接受了该事件，避免合成抢跑或积累付费请求。
                                delivery.accepted.await()
                            }
                        } finally {
                            responses.close()
                        }
                    }
                    worker.invokeOnCompletion { responses.close() }
                    synchronized(lock) {
                        if (closed) worker.cancel(ConnectionClosed()) else active = worker
                    }
                    worker.start()
                    try {
                        for (delivery in responses) {
                            emit(delivery.response)
                            delivery.accepted.complete(Unit)
                        }
                        try {
                            worker.await()
                        } catch (_: TurnInterrupted) {
                            currentCoroutineContext().ensureActive()
                            emit(LlmResponse(interrupted = true, turnComplete = true))
                        } catch (_: ConnectionClosed) {
                            currentCoroutineContext().ensureActive()
                        }
                    } finally {
                        synchronized(lock) { if (active === worker) active = null }
                        withContext(NonCancellable) { worker.cancelAndJoin() }
                    }
                }
            } finally {
                collecting.unlock()
            }
        }

        private suspend fun respond(turn: Turn, send: suspend (LlmResponse) -> Unit) {
            val text = when (turn) {
                is Turn.Text -> turn.text.trim()
                is Turn.Audio -> transcribe(turn.pcm16, send)
            }
            if (text.isEmpty()) {
                send(LlmResponse(turnComplete = true))
                return
            }
            val contents = synchronized(lock) {
                if (closed) throw ConnectionClosed()
                history.add(Content.fromText(Role.USER, text))
                history.toList()
            }
            val answer = StringBuilder()
            // 服务支持会话式合成时用它：文本随模型增量投递，首包不必等整句。
            val session = synthesis.openSession()
            var completed = false
            try {
                if (session != null) {
                    respondWithSession(session, contents, answer, send)
                } else {
                    respondWithOneShot(contents, answer, send)
                }
                completed = true
            } finally {
                session?.let { open ->
                    withContext(NonCancellable) {
                        // 被打断时通知服务端停止合成，避免服务端继续合成并计费。
                        if (!completed) open.cancel()
                        open.close()
                    }
                }
            }
            synchronized(lock) { if (!closed) history.add(Content.fromText(Role.MODEL, answer.toString())) }
            send(LlmResponse(turnComplete = true))
        }

        /**
         * 会话式合成：文本随模型增量逐次投递，音频随服务端切句下发。
         *
         * 服务端按自己的标点规则切句，与客户端的切句大体一致；片段文本优先取客户端切出的句子，
         * 因为它在播放开始前就已知，字幕与播放确认据此；客户端没有对应句子时回退到服务端字幕。
         */
        private suspend fun respondWithSession(
            session: SpeechSynthesisSession,
            contents: List<Content>,
            answer: StringBuilder,
            send: suspend (LlmResponse) -> Unit,
        ) = coroutineScope {
            // 文字增量与音频片段都走同一条出口：用互斥保证一次只有一个在发。
            val emitLock = Mutex()
            val emit: suspend (LlmResponse) -> Unit = { response -> emitLock.withLock { send(response) } }
            val clientSentences = ConcurrentLinkedQueue<String>()
            val generation = async {
                val buffer = VoiceSentenceBuffer()
                generate(contents, answer) { body ->
                    emit(LlmResponse(content = Content.fromText(Role.MODEL, body), partial = true))
                    buffer.append(body).forEach { clientSentences.add(it) }
                    // 逐次投递：服务端拿到半句就能开始合成，首包不必等整句。
                    session.send(body)
                }
                buffer.finish().takeIf { it.isNotEmpty() }?.let { clientSentences.add(it) }
                // 让服务端把缓冲里的文本合成并下发完毕，此后本轮音频才算完整。
                session.flush()
            }
            val playback = async {
                session.segments.collect { segment ->
                    // 假设服务端片段与客户端切句一一对应（_subtitle_enable + sentence 切分）；
                    // 若服务端拆分/合并片段，这里回退用服务端字幕文本兜底。
                    val segmentText = clientSentences.poll() ?: segment.text.value
                    emit(LlmResponse(outputTranscription = if (request.liveConnectConfig.outputAudioTranscription != null)
                        Transcription(text = segmentText, finished = false) else null,
                        customMetadata = mapOf(VoiceAudioMarkers.SEGMENT_START to true)))
                    segment.audio.collect { bytes ->
                        emit(LlmResponse(content = Content(role = Role.MODEL, parts = listOf(
                            Part(inlineData = Blob(mimeType = "audio/pcm;rate=24000", data = bytes)),
                        )), partial = true))
                    }
                    emit(LlmResponse(outputTranscription = if (request.liveConnectConfig.outputAudioTranscription != null)
                        Transcription(finished = true) else null,
                        customMetadata = mapOf(VoiceAudioMarkers.SEGMENT_END to true)))
                }
            }
            generation.await()
            playback.await()
        }

        /** 一次性合成回退：整句合成，句间没有流式衔接。 */
        private suspend fun respondWithOneShot(
            contents: List<Content>,
            answer: StringBuilder,
            send: suspend (LlmResponse) -> Unit,
        ) = coroutineScope {
            val sentences = Channel<String>(1)
            val output = async {
                for (sentence in sentences) {
                    send(LlmResponse(outputTranscription = if (request.liveConnectConfig.outputAudioTranscription != null)
                        Transcription(text = sentence, finished = false) else null,
                        customMetadata = mapOf(VoiceAudioMarkers.SEGMENT_START to true)))
                    flow { synthesis.synthesize(sentence).collect { emit(it) } }.catch { cause ->
                        if (cause is CancellationException) throw cause
                        throw VoiceAudioBackendException(RealtimeVoiceFailure.Synthesis, cause = cause)
                    }.collect { bytes ->
                        send(LlmResponse(content = Content(role = Role.MODEL, parts = listOf(
                            Part(inlineData = Blob(mimeType = "audio/pcm;rate=24000", data = bytes)),
                        )), partial = true))
                    }
                    send(LlmResponse(outputTranscription = if (request.liveConnectConfig.outputAudioTranscription != null)
                        Transcription(finished = true) else null,
                        customMetadata = mapOf(VoiceAudioMarkers.SEGMENT_END to true)))
                }
            }
            try {
                val buffer = VoiceSentenceBuffer()
                generate(contents, answer) { body ->
                    send(LlmResponse(content = Content.fromText(Role.MODEL, body), partial = true))
                    buffer.append(body).forEach { sentences.send(it) }
                }
                buffer.finish().takeIf { it.isNotEmpty() }?.let { sentences.send(it) }
            } finally {
                sentences.close()
            }
            output.await()
        }

        /**
         * 流式转写：中间结果透出给调用方显示，最终结果作为本轮输入。
         *
         * 服务未接入流式识别时只产出最终结果，调用方因此可以统一按流消费。
         */
        private suspend fun transcribe(pcm16: ByteArray, send: suspend (LlmResponse) -> Unit): String {
            val wantsTranscript = request.liveConnectConfig.inputAudioTranscription != null
            var final = ""
            recognition.transcribeStreaming(pcm16).collect { event ->
                when (event) {
                    is RecognitionEvent.Partial -> if (wantsTranscript) {
                        send(LlmResponse(inputTranscription = Transcription(text = event.text.trim(), finished = false)))
                    }
                    is RecognitionEvent.Final -> {
                        final = event.text.trim()
                        if (wantsTranscript) {
                            send(LlmResponse(inputTranscription = Transcription(text = final, finished = true)))
                        }
                    }
                }
            }
            return final
        }

        /** 跑一遍文字模型，把增量正文交给 [onDelta]，并把完整正文累积到 [answer]。 */
        private suspend fun generate(
            contents: List<Content>,
            answer: StringBuilder,
            onDelta: suspend (String) -> Unit,
        ) {
            var hasPartial = false
            val live = request.liveConnectConfig
            val textRequest = request.copy(model = llmModel, contents = contents, config = request.config.copy(
                systemInstruction = request.config.systemInstruction ?: live.systemInstruction,
                thinkingConfig = request.config.thinkingConfig ?: live.thinkingConfig,
                temperature = live.temperature ?: request.config.temperature,
                topP = live.topP ?: request.config.topP,
                topK = live.topK ?: request.config.topK,
                maxOutputTokens = live.maxOutputTokens ?: request.config.maxOutputTokens,
                seed = live.seed ?: request.config.seed,
                safetySettings = live.safetySettings ?: request.config.safetySettings,
                mediaResolution = live.mediaResolution ?: request.config.mediaResolution,
                // Live 的 AUDIO 描述组合结果，不应要求底层文字模型生成音频。
                responseModalities = null,
            ))
            llmModel.generateContent(textRequest, stream = true).collect { response ->
                // 带上厂商错误码与原文：简单聊天路径外层不留完整异常，真机只能靠这条消息定位。
                check(response.errorCode == null && response.errorMessage.isNullOrBlank()) {
                    "Voice model request failed: ${response.errorCode} ${response.errorMessage}"
                }
                val body = response.content?.parts.orEmpty().filter { it.thought != true }
                    .mapNotNull { it.text }.joinToString("")
                if (body.isNotEmpty() && (response.partial || !hasPartial)) {
                    if (response.partial) hasPartial = true
                    answer.append(body)
                    onDelta(body)
                }
            }
        }

        override suspend fun closeSession() {
            val worker = synchronized(lock) {
                if (closed) return
                closed = true
                history.clear()
                textInput.clear()
                audioInput.reset()
                turns.cancel()
                active.also { it?.cancel(ConnectionClosed()) }
            }
            worker?.join()
        }
    }

    /** 一轮输入，仅持有用户文字或有界的完整 PCM 活动。 */
    private sealed interface Turn {
        /** 合并 partial 内容后的完整文字轮。 */
        data class Text(val text: String) : Turn
        /** 由手动活动边界封装的 16 kHz PCM16 语句。 */
        data class Audio(val pcm16: ByteArray) : Turn {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Audio

                return pcm16.contentEquals(other.pcm16)
            }

            override fun hashCode(): Int {
                return pcm16.contentHashCode()
            }
        }
    }

    /** 内部交付确认，只有下游处理完事件才允许对应生产者继续。 */
    private data class Delivery(val response: LlmResponse, val accepted: CompletableDeferred<Unit>)
    private class TurnInterrupted : CancellationException("Live voice turn interrupted")
    private class ConnectionClosed : CancellationException("Live voice connection closed")

    private companion object {
        const val MAX_INPUT_BYTES = 16_000 * 2 * 30
    }
}
