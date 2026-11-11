package github.ponyhuang.gimi.data.voice.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
import github.ponyhuang.gimi.domain.voice.repository.VoiceAuthenticationException
import github.ponyhuang.gimi.domain.voice.repository.VoiceServiceException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/** MiMo 语音服务标识；与模型目录中的服务 ID 一致。 */
internal const val MIMO_SPEECH_SERVICE_ID = "mimo-speech"

/**
 * MiMo Audio（V2.5 系列）的对话协议音频能力。
 *
 * MiMo 与 OpenAI 同走 `/chat/completions`，但请求契约并不相同，因此独立实现而不是
 * 复用 OpenAI 兼容协议（官方文档 mimo.mi.com/docs/zh-CN/quick-start/usage-guide/audio）：
 * - 合成：目标文本必须放在 `assistant` 消息（可选 `user` 消息做风格指令，本链路不传），
 *   `audio` 只带 `format` + `voice`；流式返回 `choices[].delta.audio.data` 的 base64
 *   PCM16（24 kHz 单声道，与播放器格式一致）。
 * - 识别：`input_audio.data` 是带 `data:` 前缀的 data-URI（区别于 OpenAI 的裸 base64
 *   加独立 format 字段），语种经 MiMo 专属的 `asr_options.language` 下发（auto/zh/en）。
 *
 * 音色表同样独立：预置音色见 `MiMoTtsVoices`（mimo_default/冰糖/茉莉/…），与 OpenAI
 * 音色互不通用。传输层的 SSE 解析与 OpenAI 兼容协议同形但刻意不复用：两家的协议
 * 各自独立演进，隔离实现避免一方改字段牵连另一方。
 */
class MiMoSpeechProtocol(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson = Gson(),
) {
    /** 合成：以 SSE 逐行下发音频分块。 */
    fun synthesize(request: SynthesisRequest): Flow<ByteArray> = flow {
        val payload = JsonObject().apply {
            addProperty("model", request.modelId)
            // MiMo 约定：待合成文本放在 assistant 消息；user 消息是可选的风格指令，这里不传
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "assistant", "content" to request.text))))
            add(
                "audio",
                JsonObject().apply {
                    addProperty("format", AUDIO_FORMAT_PCM16)
                    // 留空走官方默认音色 mimo_default，而不是让请求直接 400
                    addProperty("voice", request.voiceId.ifBlank { DEFAULT_VOICE })
                },
            )
            addProperty("stream", true)
        }
        val httpRequest = Request.Builder()
            .url(request.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${request.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        // 付费的合成请求禁用 OkHttp 的连接失败隐式重试，避免请求体已缓冲时静默重发、重复计费
        val call = okHttpClient.newBuilder().retryOnConnectionFailure(false).build().newCall(httpRequest)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string()?.take(500).orEmpty()
                    val message = "语音合成失败（HTTP ${response.code}）" +
                        detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
                    // 401/403 是密钥问题，与服务端业务错误是两类不同的用户问题。
                    if (response.code == 401 || response.code == 403) {
                        throw VoiceAuthenticationException(message)
                    }
                    throw VoiceServiceException(message)
                }
                val source = response.body?.source() ?: throw IOException("语音合成响应为空")
                var emittedAudio = false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") {
                        if (data == "[DONE]") break
                        continue
                    }
                    val audioData = runCatching {
                        gson.fromJson(data, JsonObject::class.java)
                            .getAsJsonArray("choices")
                            ?.firstOrNull()
                            ?.asJsonObject
                            ?.getAsJsonObject("delta")
                            ?.getAsJsonObject("audio")
                            ?.get("data")
                            ?.asString
                    }.getOrNull()
                    if (!audioData.isNullOrBlank()) {
                        // 与 realtime 协议一致用 okio 解码：Android/JVM 通用，非法数据返回 null
                        val bytes = audioData.decodeBase64()?.toByteArray()
                            ?: throw IOException("语音合成返回了无效音频数据")
                        if (bytes.isNotEmpty()) {
                            emittedAudio = true
                            emit(bytes)
                        }
                    }
                }
                if (!emittedAudio) throw IOException("语音合成未返回音频数据")
            }
        } finally {
            cancellation.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** 识别：整段音频一次请求，返回最终文本。 */
    suspend fun recognize(request: RecognitionRequest): String = withContext(Dispatchers.IO) {
        val wav = encodePcm16Wav(
            pcm16 = request.pcm16,
            sampleRateHz = request.sampleRateHz,
            channelCount = request.channelCount,
        )
        val body = gson.toJson(
            mapOf(
                "model" to request.modelId,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf(
                                "type" to "input_audio",
                                // MiMo 要求 data-URI 形态的音频数据；只支持 wav/mp3
                                "input_audio" to mapOf(
                                    "data" to "data:audio/wav;base64," + wav.toByteString().base64(),
                                ),
                            ),
                        ),
                    ),
                ),
                // 语种 auto/zh/en；明确语种时识别更准，留空按自动检测
                "asr_options" to mapOf("language" to request.language.ifBlank { "auto" }),
            ),
        )
        val httpRequest = Request.Builder()
            .url(request.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${request.apiKey}")
            .addHeader("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val responseBody = okHttpClient.newBuilder().retryOnConnectionFailure(false).build()
            .newCall(httpRequest).awaitSpeechRecognitionBody(includeErrorBody = true)
        JsonParser.parseString(responseBody)
            .asJsonObject
            .getAsJsonArray("choices")
            ?.asSequence()
            ?.mapNotNull { choice ->
                choice.asJsonObject
                    .getAsJsonObject("message")
                    ?.get("content")
                    ?.takeUnless { it.isJsonNull }
                    ?.asString
            }
            ?.firstOrNull(String::isNotBlank)
            ?: throw IOException("语音识别未返回文本")
    }

    private companion object {
        const val AUDIO_FORMAT_PCM16 = "pcm16"
        const val DEFAULT_VOICE = "mimo_default"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** MiMo 的语音合成能力；预置音色表独立于 OpenAI，见 [github.ponyhuang.gimi.domain.voice.model.MiMoTtsVoices]。 */
class MiMoSynthesizer @Inject constructor(
    private val protocol: MiMoSpeechProtocol,
) : SpeechSynthesizer {
    override val serviceIds: Set<String> = setOf(MIMO_SPEECH_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    override fun synthesize(request: SynthesisRequest): Flow<ByteArray> = protocol.synthesize(request)
}

/** MiMo 的音频识别能力；`mimo-v2.5-asr` 是对话协议承载的专用转写模型。 */
class MiMoAudioRecognizer @Inject constructor(
    private val protocol: MiMoSpeechProtocol,
) : SpeechRecognizer {
    override val serviceIds: Set<String> = setOf(MIMO_SPEECH_SERVICE_ID)

    /**
     * 该协议同时承载专用转写（STT）与音频理解（ASR）两种语义：
     * 二者都是把音频交给对话模型，区别在模型自身是什么。
     */
    override val capabilities: Set<ModelCapability> =
        setOf(ModelCapability.STT, ModelCapability.ASR)

    override suspend fun recognize(request: RecognitionRequest): String = protocol.recognize(request)
}
