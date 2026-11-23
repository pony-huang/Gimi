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
import github.ponyhuang.gimi.core.audio.encodePcm16Wav
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioMarkers
import github.ponyhuang.gimi.domain.voice.repository.VoiceSentenceBuffer
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

/**
 * 将全模态 LLM 与 TTS 实现为 ADK 官方 Model/LiveConnection（半级联模式）。
 *
 * 与全级联（[CascadedVoiceModel]）的区别：半级联没有独立的 ASR 语音识别阶段，
 * 用户的音频直接封装为 WAV 格式的输入模态传给全模态 LLM；全模态 LLM 的流式输出文本
 * 依然由 TTS 进行切句合成与播放，对外呈现标准 Live 语音连接契约。
 */
class SemiCascadedVoiceModel(
    private val llmModel: Model,
    private val synthesis: SpeechSynthesisRepository,
    /** 合成音色的会话级覆盖；null 表示沿用全局合成音色或厂商默认。 */
    private val synthesisVoice: String? = null,
    private val synthesisModel: ModelSelection?,
) : Model {
    override val name: String = "semi-cascaded:${llmModel.name}"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        llmModel.generateContent(request.copy(model = llmModel), stream)

    override suspend fun connect(request: LlmRequest): LiveConnection {
        val live = request.liveConnectConfig
        require(live.responseModalities.isNullOrEmpty() || live.responseModalities == listOf(Modality.AUDIO)) {
            "Semi-cascaded voice requires AUDIO output"
        }
        require(live.tools.isNullOrEmpty() && request.config.tools.isNullOrEmpty()) { "Semi-cascaded voice has no tools" }
        require(live.realtimeInputConfig?.activityHandling == null && live.realtimeInputConfig?.turnCoverage == null) {
            "Semi-cascaded voice does not support custom activity handling or turn coverage"
        }
        require(live.sessionResumption == null && live.contextWindowCompression == null &&
            live.translationConfig == null && live.proactivity == null && live.enableAffectiveDialog != true &&
            live.explicitVadSignal != true && live.speechConfig == null) {
            "Unsupported semi-cascaded voice configuration; voice selection belongs to speech settings"
        }
        return Connection(request)
    }

    private inner class Connection(private val request: LlmRequest) : LiveConnection {
        private val lock = Any()
        private val collecting = Mutex()
        private val turns = Channel<Turn>(1)
        private val history = mutableListOf<Content>()
        private val textInput = StringBuilder()
        private val mediaInput = mutableListOf<Part>()
        private val audioInput = ByteArrayOutputStream()
        private var historySent = false
        private var inputSent = false
        private var recording = false
        private var closed = false
        private var active: Job? = null

        override suspend fun sendHistory(history: List<Content>) {
            val seeded = history.map { content ->
                content.copy(parts = content.parts.filter { it.thought != true && (it.text != null ||
                    (content.role == Role.USER && (it.fileData != null || it.inlineData != null))) })
            }.filter { it.parts.isNotEmpty() }
            synchronized(lock) {
                check(!closed && !historySent && !inputSent) { "History must be sent once before input" }
                historySent = true
                this.history.addAll(if (seeded.lastOrNull()?.role == Role.USER) seeded.dropLast(1) else seeded)
            }
            seeded.lastOrNull()?.takeIf { it.role == Role.USER }?.let { sendContent(it) }
        }

        override suspend fun sendContent(content: Content, partial: Boolean) {
            ContentInput(content, partial)
            require(content.role == Role.USER && content.parts.all { it.text != null || it.fileData != null || it.inlineData != null }) {
                "Semi-cascaded voice accepts user text and media"
            }
            val turn = synchronized(lock) {
                check(!closed)
                check(!recording) { "Cannot mix a text turn with an open audio activity" }
                inputSent = true
                require(textInput.length + content.parts.sumOf { it.text.orEmpty().length } <= MAX_INPUT_BYTES) {
                    "Partial text exceeds the voice input limit"
                }
                textInput.append(content.parts.joinToString("") { it.text.orEmpty() })
                mediaInput.addAll(content.parts.filter { it.text == null })
                if (partial) null else Turn.Text(Content(role = Role.USER, parts = buildList {
                    textInput.toString().takeIf(String::isNotBlank)?.let { add(Part(text = it)) }
                    addAll(mediaInput)
                }).also { textInput.clear(); mediaInput.clear() })
            }
            if (turn != null) {
                interrupt()
                turns.send(turn)
            }
        }

        override suspend fun sendRealtime(input: RealtimeInput) {
            require(request.liveConnectConfig.realtimeInputConfig?.automaticActivityDetection?.disabled == true) {
                "Semi-cascaded audio requires manual activity detection"
            }
            val turn = synchronized(lock) {
                check(!closed)
                check(textInput.isEmpty() && mediaInput.isEmpty()) { "Finish the partial text turn before sending audio" }
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
                    is RealtimeInput.Video -> throw UnsupportedOperationException("Semi-cascaded voice does not accept video")
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
            val input = when (turn) {
                is Turn.Text -> turn.content
                is Turn.Audio -> {
                    // 全模态 LLM 直传整段音频 WAV，无 ASR 环节
                    val wav = encodePcm16Wav(turn.pcm16, sampleRateHz = 16_000, channelCount = 1)
                    Content(
                        role = Role.USER,
                        parts = listOf(
                            Part(
                                inlineData = Blob(
                                    mimeType = "audio/wav",
                                    data = wav,
                                ),
                            ),
                        ),
                    )
                }
            }
            val contents = synchronized(lock) {
                if (closed) throw ConnectionClosed()
                history.add(input)
                history.toList()
            }
            val answer = StringBuilder()
            val session = synthesis.openSession(synthesisVoice, synthesisModel)
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
                        if (!completed) open.cancel()
                        open.close()
                    }
                }
            }
            synchronized(lock) { if (!closed) history.add(Content.fromText(Role.MODEL, answer.toString())) }
            send(LlmResponse(turnComplete = true))
        }

        private suspend fun respondWithSession(
            session: SpeechSynthesisSession,
            contents: List<Content>,
            answer: StringBuilder,
            send: suspend (LlmResponse) -> Unit,
        ) = coroutineScope {
            val emitLock = Mutex()
            val emit: suspend (LlmResponse) -> Unit = { response -> emitLock.withLock { send(response) } }
            val clientSentences = ConcurrentLinkedQueue<String>()
            val generation = async {
                val buffer = VoiceSentenceBuffer()
                try {
                    generate(contents, answer) { body ->
                        emit(LlmResponse(content = Content.fromText(Role.MODEL, body), partial = true))
                        buffer.append(body).forEach { sentence ->
                            clientSentences.add(sentence)
                            // 逐句投递：缓冲切句后按完整句子投递，避免逐 token 发送频繁触发厂商 RPM 限流。
                            session.send(sentence)
                        }
                    }
                    buffer.finish().takeIf { it.isNotEmpty() }?.let { sentence ->
                        clientSentences.add(sentence)
                        session.send(sentence)
                    }
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    throw VoiceAudioBackendException(RealtimeVoiceFailure.Generation, cause = cause)
                }
                try {
                    session.flush()
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    throw VoiceAudioBackendException(RealtimeVoiceFailure.Synthesis, cause = cause)
                }
            }
            val playback = async {
                try {
                    session.segments.collect { segment ->
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
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    throw VoiceAudioBackendException(RealtimeVoiceFailure.Synthesis, cause = cause)
                }
            }
            generation.await()
            playback.await()
        }

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
                    flow { synthesis.synthesize(sentence, synthesisVoice, synthesisModel).collect { emit(it) } }.catch { cause ->
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
                responseModalities = null,
            ))
            llmModel.generateContent(textRequest, stream = true).collect { response ->
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

    private sealed interface Turn {
        data class Text(val content: Content) : Turn
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

    private data class Delivery(val response: LlmResponse, val accepted: CompletableDeferred<Unit>)
    private class TurnInterrupted : CancellationException("Live voice turn interrupted")
    private class ConnectionClosed : CancellationException("Live voice connection closed")

    private companion object {
        const val MAX_INPUT_BYTES = 16_000 * 2 * 30
    }
}
