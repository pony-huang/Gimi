@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import android.util.Log
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.AudioTranscriptionConfig
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.LiveConnectConfig
import com.google.adk.kt.types.Modality
import com.google.adk.kt.types.Role
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceConfiguration
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceDuplexSession
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import github.ponyhuang.gimi.domain.voice.repository.RecognitionEvent
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackend
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendSession
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioEvent
import github.ponyhuang.gimi.domain.voice.repository.isRealtimeEngine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 应用会话与语音后端的唯一转换层；设备、AEC 和播放仍由 data:speech 拥有。
 * 简单聊天走分轮重建的级联模型（内存上下文 + 可选预设）；聊天模式直接接入
 * 完整 chat agent（[VoiceAgentConversation]），工具全量自动批准，文字由 ADK
 * Runner 持久化到目标聊天。
 */
class AdkVoiceAudioBackend @Inject constructor(
    private val models: VoiceLiveModelFactory,
    private val recognition: SpeechRecognitionRepository,
    private val synthesis: SpeechSynthesisRepository,
    private val simpleChatPrompts: SimpleChatPromptRepository,
    private val agentVoice: Provider<VoiceAgentConversation>,
    private val assistantTools: RealtimeAssistantTools,
) : VoiceAudioBackend {
    override fun open(configuration: RealtimeVoiceConfiguration): VoiceAudioBackendSession =
        if (configuration.mode == RealtimeVoiceMode.Chat) AgentSession(configuration) else SimpleSession(configuration)

    override suspend fun openDuplex(configuration: RealtimeVoiceConfiguration): RealtimeVoiceDuplexSession {
        check(configuration.mode.isRealtimeEngine) { "openDuplex 仅支持 Realtime 模式" }
        // 与简单聊天同源的人设来源：可选用户预设（官方或自建）替换人设层，输出底线始终叠加。
        // 实时助手：会话开启前解析授权可执行的工具桥（可能探测 MCP，移到 IO 执行，
        // 不阻塞主线程）；解析失败退化为无工具的实时轻聊。实时轻聊恒无工具。
        val tools = if (configuration.mode == RealtimeVoiceMode.RealtimeAssistant) {
            // 空声明与无工具等价：门面按 tools != null 装配工具轮，空桥仍会为幻觉调用写回。
            withContext(Dispatchers.IO) { assistantTools.resolve(configuration.modelSelection) }
                ?.takeIf { it.declarations.isNotEmpty() }
        } else {
            null
        }
        // 实际可下发的声明面决定末段指令。助手模式绝不下发「You have no tools」这类
        // 绝对能力声明：一次工具面解析故障会把它固化成模型人设，用户一问工具就得到
        // 「我没有工具」；即便后续工具恢复，该指令仍压着模型。空声明时改用中性约束，
        // 只禁止虚构「已执行外部动作」，不替模型断言自己有没有工具。
        val assistantMode = configuration.mode == RealtimeVoiceMode.RealtimeAssistant
        val hasDeclarations = !tools?.declarations.isNullOrEmpty()
        val instructions = realtimeInstructions(selectedPresetContent(), assistantMode, hasDeclarations)
        Log.i(
            TAG,
            "实时会话配置: mode=${configuration.mode}, 工具声明=${tools?.declarations?.size ?: 0}, " +
                "assistantMode=$assistantMode",
        )
        return NativeRealtimeDuplexSession(
            models = models,
            selection = configuration.modelSelection,
            instructions = instructions,
            // 会话音色的自定义覆盖；null 表示用所选模型在该厂商下的缺省音色。
            voice = configuration.realtimeVoice,
            tools = tools,
            // 门面构造非挂起，上行契约在首个事件/首帧上行时惰性解析；未知 Realtime
            // 服务无原生适配器，此处显式失败而不是静默走错协议。
            uplinkProvider = {
                models.realtimeUplink(configuration.modelSelection)
                    ?: error("所选服务不是可建立全双工会话的 Realtime 服务")
            },
        )
    }

    /**
     * 当前选中的预设正文；选中官方预设或用户自建预设都从这里解析，未选中时为 null。
     * 官方预设与自建预设共用同一套 id 空间，命中顺序是先官方后自建。
     */
    private fun selectedPresetContent(): String? {
        val id = simpleChatPrompts.selectedPresetId.value ?: return null
        return simpleChatPrompts.builtInPresets.firstOrNull { it.id == id }?.content
            ?: simpleChatPrompts.presets.value.firstOrNull { it.id == id }?.content
    }

    /** 简单聊天：级联模型 + 内存上下文，无工具、无持久化。 */
    private inner class SimpleSession(private val configuration: RealtimeVoiceConfiguration) : VoiceAudioBackendSession {
        private val mutex = Mutex()
        private val heardHistory = ArrayDeque<Pair<String, String>>()
        private var pendingInput: String? = null
        @Volatile private var closed = false

        /** 每轮读取当前选中的简单聊天预设；指令按轮重建，中途切换预设下一轮即生效。 */
        private fun simpleChatInstruction(): String? = selectedPresetContent()

        override suspend fun isAvailable(): Boolean {
            check(!closed)
            return models.isAvailable(configuration.modelSelection)
        }

        override fun transcribe(pcm16: ByteArray): Flow<RecognitionEvent> = flow {
            check(!closed)
            emitAll(recognition.transcribeStreaming(pcm16))
        }

        override fun respond(text: String, previousSpokenText: String): Flow<VoiceAudioEvent> = flow {
            require(text.isNotBlank())
            mutex.withLock {
                check(!closed)
                pendingInput?.let { heardHistory.addLast(it to previousSpokenText) }
                while (heardHistory.size > 32) heardHistory.removeFirst()
                pendingInput = text
                val model = models.create(configuration.modelSelection)
                // 简单聊天可选用户预设替换人设层，未选时回退内置语音人设；输出底线与
                // 「无工具」声明始终叠加（级联链路确实无任何工具）。
                val instruction = (simpleChatInstruction() ?: VOICE_INSTRUCTION) + VOICE_OUTPUT_RULES + VOICE_NO_TOOLS_RULE
                val connection = model.connect(LlmRequest(
                    model = model,
                    config = GenerateContentConfig(
                        systemInstruction = Content.fromText(Role.SYSTEM, instruction),
                        // 保留模型默认思考模式；MiniMax M3 等模型拒绝 disabled，thought 内容在下游过滤。
                    ),
                    liveConnectConfig = LiveConnectConfig(
                        responseModalities = listOf(Modality.AUDIO),
                        inputAudioTranscription = AudioTranscriptionConfig(),
                        outputAudioTranscription = AudioTranscriptionConfig(),
                    ),
                ))
                var segment: AudioSegment? = null
                val pendingTranscript = StringBuilder()
                try {
                    connection.sendHistory(buildList {
                        for ((input, spoken) in heardHistory) {
                            add(Content.fromText(Role.USER, input))
                            if (spoken.isNotBlank()) add(Content.fromText(Role.MODEL, spoken))
                        }
                        // 官方 sendHistory 会回答末尾 USER；把当前问题一起初始化，避免旧的未听轮先触发回答。
                        add(Content.fromText(Role.USER, text))
                    })
                    var completed = false
                    var interrupted = false
                    var hasPartialText = false
                    var hasModelText = false
                    connection.receive().takeWhile { response ->
                        if (response.errorCode != null || !response.errorMessage.isNullOrBlank()) {
                            throw VoiceAudioBackendException(RealtimeVoiceFailure.Generation,
                                message = "Live voice model request failed: " +
                                    "${response.errorCode} ${response.errorMessage}")
                        }
                        if (response.interrupted) {
                            interrupted = true
                            segment?.chunks?.cancel()
                            segment = null
                            emit(VoiceAudioEvent.Interrupted)
                            return@takeWhile false
                        }
                        val body = response.content?.parts.orEmpty().filter { it.thought != true }
                            .mapNotNull { it.text }.joinToString("")
                        if (body.isNotEmpty() && (response.partial || !hasPartialText)) {
                            hasModelText = true
                            if (response.partial) hasPartialText = true
                            emit(VoiceAudioEvent.Text(body))
                        }
                        val startsSegment = response.customMetadata?.get(VoiceAudioMarkers.SEGMENT_START) == true
                        if (startsSegment) {
                            check(segment == null) { "Overlapping voice audio segments" }
                            segment = AudioSegment()
                        }
                        response.outputTranscription?.text?.takeIf { it.isNotEmpty() }?.let { transcript ->
                            if (!hasModelText) emit(VoiceAudioEvent.Text(transcript))
                            val current = segment
                            if (current == null) pendingTranscript.append(transcript)
                            else current.text.value += transcript
                        }
                        if (startsSegment) {
                            val current = checkNotNull(segment)
                            emit(VoiceAudioEvent.Audio(current.text.asStateFlow(), current.audio(), current.id))
                            // 可选分句提示使组合后端保持惰性合成；原生后端只依赖标准音频/转写事件。
                            current.started.await()
                        }
                        for (part in response.content?.parts.orEmpty().filter { it.thought != true }) {
                            val audio = part.inlineData ?: continue
                            require(audio.mimeType == "audio/pcm;rate=24000") { "Expected mono PCM16 at 24 kHz" }
                            if (segment == null) {
                                segment = AudioSegment().also { it.text.value = pendingTranscript.toString() }
                                pendingTranscript.clear()
                                val current = checkNotNull(segment)
                                emit(VoiceAudioEvent.Audio(current.text.asStateFlow(), current.audio(), current.id))
                                current.started.await()
                            }
                            val current = checkNotNull(segment)
                            current.chunks.send(checkNotNull(audio.data).copyOf())
                        }
                        completed = response.turnComplete == true
                        if (response.customMetadata?.get(VoiceAudioMarkers.SEGMENT_END) == true || completed) {
                            segment?.chunks?.close()
                            segment = null
                        }
                        !completed
                    }.collect {}
                    check(completed || interrupted) { "Live voice connection ended before the turn completed" }
                } finally {
                    segment?.chunks?.cancel()
                    withContext(NonCancellable) { connection.closeSession() }
                }
            }
        }.flowOn(Dispatchers.IO)

        override fun confirmPlayback(segmentId: String, text: String) {}

        override suspend fun finishTurn() {}

        override suspend fun retryPersistence() {}

        override suspend fun close() {
            mutex.withLock {
                closed = true
                heardHistory.clear()
                pendingInput = null
            }
        }
    }

    /**
     * 聊天模式：识别文字交给完整 chat agent，正文增量按句送入 TTS。
     * 会话历史与持久化由 ADK Runner 负责；播完确认与重试不再需要。
     */
    private inner class AgentSession(private val configuration: RealtimeVoiceConfiguration) : VoiceAudioBackendSession {
        private val agent = agentVoice.get().bind(
            requireNotNull(configuration.conversationId) { "Chat voice requires a conversation" },
            requireNotNull(configuration.modelSelection) { "Chat voice requires a model selection" },
        )
        private val mutex = Mutex()
        @Volatile private var closed = false

        override suspend fun isAvailable(): Boolean {
            check(!closed)
            if (!models.isAvailable(configuration.modelSelection)) return false
            check(!closed)
            agent.acquire()
            return true
        }

        override fun transcribe(pcm16: ByteArray): Flow<RecognitionEvent> = flow {
            check(!closed)
            emitAll(recognition.transcribeStreaming(pcm16))
        }

        // channelFlow 而非 flow：文本增量与音频片段分别来自生成/播放两个并发端，
        // flow 构建器的单协程发射不变量会在第一句正文处直接失败。
        override fun respond(text: String, previousSpokenText: String): Flow<VoiceAudioEvent> = channelFlow {
            require(text.isNotBlank())
            mutex.withLock {
                check(!closed)
                val tts = synthesis.openSession()
                var completed = false
                try {
                    if (tts != null) {
                        respondWithSessionTts(tts, text) { send(it) }
                        completed = true
                    } else {
                        respondWithOneShotTts(text) { send(it) }
                        completed = true
                    }
                } finally {
                    tts?.let { open ->
                        withContext(NonCancellable) {
                            // 被打断时通知服务端停止合成，避免服务端继续合成并计费。
                            if (!completed) open.cancel()
                            open.close()
                        }
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

        override fun confirmPlayback(segmentId: String, text: String) {}

        override suspend fun finishTurn() {}

        override suspend fun retryPersistence() {}

        override suspend fun close() {
            mutex.withLock {
                closed = true
                agent.release()
            }
        }

        /**
         * 会话式合成：agent 正文增量逐句投递，音频随服务端切句下发。
         * 片段文本优先取客户端切出的句子（播放开始前已知），无对应句子时回退服务端字幕。
         * 生成侧失败会立刻打断片段消费并外抛，不会留下永远等不到收尾的片段流。
         */
        private suspend fun respondWithSessionTts(
            tts: SpeechSynthesisSession,
            text: String,
            emit: suspend (VoiceAudioEvent) -> Unit,
        ) = coroutineScope {
            val emitLock = Mutex()
            val deliver: suspend (VoiceAudioEvent) -> Unit = { event -> emitLock.withLock { emit(event) } }
            val clientSentences = ArrayDeque<String>()
            val buffer = VoiceSentenceBuffer()
            val generation = async {
                agent.runTurn(text) { delta ->
                    deliver(VoiceAudioEvent.Text(delta))
                    buffer.append(delta).forEach { sentence ->
                        synchronized(clientSentences) { clientSentences.addLast(sentence) }
                        tts.send(sentence)
                    }
                }
                buffer.finish().takeIf { it.isNotEmpty() }?.let { sentence ->
                    synchronized(clientSentences) { clientSentences.addLast(sentence) }
                    tts.send(sentence)
                }
                // 让服务端把缓冲里的文本合成并下发完毕，此后本轮音频才算完整。
                tts.flush()
            }
            val playback = async {
                tts.segments.collect { segment ->
                    val sentence = synchronized(clientSentences) { clientSentences.removeFirstOrNull() }
                        ?: segment.text.value
                    deliver(VoiceAudioEvent.Audio(MutableStateFlow(sentence), segment.audio, UUID.randomUUID().toString()))
                }
            }
            // 先等生成侧：失败即抛出并取消片段消费；成功后片段流已在 flush 请求下走向收尾。
            generation.await()
            playback.await()
        }

        /** 一次性合成回退：整句合成，句间没有流式衔接。 */
        private suspend fun respondWithOneShotTts(text: String, emit: suspend (VoiceAudioEvent) -> Unit) = coroutineScope {
            val emitLock = Mutex()
            val deliver: suspend (VoiceAudioEvent) -> Unit = { event -> emitLock.withLock { emit(event) } }
            val sentences = Channel<String>(1)
            val output = launch {
                for (sentence in sentences) {
                    deliver(VoiceAudioEvent.Audio(
                        MutableStateFlow(sentence),
                        flow { synthesis.synthesize(sentence).collect { emit(it) } }.catch { cause ->
                            if (cause is CancellationException) throw cause
                            throw VoiceAudioBackendException(RealtimeVoiceFailure.Synthesis, cause = cause)
                        },
                        UUID.randomUUID().toString(),
                    ))
                }
            }
            try {
                val buffer = VoiceSentenceBuffer()
                agent.runTurn(text) { delta ->
                    deliver(VoiceAudioEvent.Text(delta))
                    buffer.append(delta).forEach { sentences.send(it) }
                }
                buffer.finish().takeIf { it.isNotEmpty() }?.let { sentences.send(it) }
            } finally {
                sentences.close()
            }
            output.join()
        }
    }

    /** 简单聊天单句 PCM 交付槽；开始收集才授权生产，取消播放会唤醒被背压阻塞的连接。 */
    private class AudioSegment {
        val id = UUID.randomUUID().toString()
        val text = MutableStateFlow("")
        val started = CompletableDeferred<Unit>()
        val chunks = Channel<ByteArray>(Channel.RENDEZVOUS)

        fun audio(): Flow<ByteArray> = flow {
            check(started.complete(Unit)) { "Voice audio may only be collected once" }
            try {
                for (chunk in chunks) emit(chunk)
            } finally {
                chunks.cancel()
            }
        }
    }

    companion object {
        private const val TAG = "AdkVoiceAudioBackend"

        /** 语音人设层：未选预设时的默认助手风格，可被简单聊天预设完全替换。提示词统一用英文撰写（与主指令一致）。 */
        internal const val VOICE_INSTRUCTION = "You are a natural, friendly voice assistant talking with the user in a real-time voice call." +
            " Converse like a real person: speak colloquially and warmly, and natural interjections such as \"okay\" and \"mm-hmm\" are fine." +
            " Respond directly first, then add the necessary details. Ask only one question at a time; if you did not hear clearly or are unsure," +
            " confirm briefly in one short sentence, never repeat the same confirmation, and never guess."

        /**
         * 语音输出底线层：约束回复形态以适配 TTS 朗读，始终拼接在人设（或预设）之后，
         * 不因用户自定义预设而失效——格式约束是语音通道的硬要求，不属于可替换的风格。
         * 回复语言锁定也放在这一层：预设会整体替换人设层，语言要求必须落在不可替换层才能始终生效。
         */
        internal const val VOICE_OUTPUT_RULES = "Reply in the user's language unless they request otherwise." +
            " Your replies are read aloud by text-to-speech. Speak only natural, conversational sentences:" +
            " answer simple questions in one or two sentences; when explaining or comparing, give the conclusion first, then the reasons;" +
            " keep complex topics in flowing narration, not a written report. Never use Markdown, lists, numbering, code blocks," +
            " parenthetical asides, emoji, or any other written formatting or symbols;" +
            " when enumerating, connect naturally with \"first..., then...\"."

        /**
         * 无工具声明（不可替换层末段）：仅用于级联链路与实时轻聊——它们确实永远没有
         * 可执行工具，明确告知模型不做外部动作承诺，避免虚构「已执行」的结果。
         */
        internal const val VOICE_NO_TOOLS_RULE = " You have no tools and cannot perform external actions; you can only help through conversation."

        /**
         * 实时助手的工具播报节奏（不可替换层末段，仅携带工具桥时拼接）：函数调用
         * 不会进入语音播报，模型必须先用一句话预告将要做什么，结果回来后用口语
         * 汇总，失败时给出替代帮助；禁止朗读原始工具输出与错误码。
         */
        internal const val REALTIME_ASSISTANT_TOOL_RULES = " You can call the tools declared in this session when they help." +
            " Function calls themselves are never spoken aloud, so before calling a tool briefly announce in one short sentence what you are about to do and vary the wording;" +
            " after the result comes back, summarize the outcome first and then the next step without reciting raw data;" +
            " if a tool fails, explain in plain words and offer another way to help, never read out error codes or technical details."

        /**
         * 助手模式但本次无可用声明时的中性约束：只禁止虚构已完成的外部动作，
         * 不替模型断言「我没有工具」。工具面可能因来源探测失败暂时为空，一句
         * 绝对能力声明会被模型当作人设长期复述，工具恢复后也难以纠正。
         */
        internal const val REALTIME_ASSISTANT_NO_TOOLS_RULE =
            " Only claim that an external action was performed when a tool actually returned a result;" +
                " otherwise help through conversation and never invent a completed action or a tool result."

        /**
         * 实时会话指令拼装（纯函数，便于单测锁定能力声明边界）。
         * 三态：有工具声明 → 工具播报节奏；助手模式但本次无声明 → 中性约束；
         * 轻聊/级联 → 明确「无工具」。
         */
        internal fun realtimeInstructions(
            preset: String?,
            assistantMode: Boolean,
            hasDeclarations: Boolean,
        ): String = buildString {
            append(preset ?: VOICE_INSTRUCTION)
            append(VOICE_OUTPUT_RULES)
            append(
                when {
                    hasDeclarations -> REALTIME_ASSISTANT_TOOL_RULES
                    assistantMode -> REALTIME_ASSISTANT_NO_TOOLS_RULE
                    else -> VOICE_NO_TOOLS_RULE
                },
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceAudioModule {
    @Binds
    abstract fun bindVoiceAudioBackend(implementation: AdkVoiceAudioBackend): VoiceAudioBackend

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindOpenAiRealtimeConnectionProbe(
        implementation: OpenAiRealtimeConnectionProbe,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindQwenRealtimeConnectionProbe(
        implementation: QwenRealtimeConnectionProbe,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindOpenAiRealtimeVoiceSupport(
        implementation: OpenAiRealtimeVoiceSupport,
    ): github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindQwenRealtimeVoiceSupport(
        implementation: QwenRealtimeVoiceSupport,
    ): github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
}
