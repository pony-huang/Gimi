package github.ponyhuang.gimi.data.modelaccess.openaicompatible

import android.util.Base64
import javax.inject.Inject
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer

import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
import github.ponyhuang.gimi.domain.voice.repository.VoiceAuthenticationException
import github.ponyhuang.gimi.domain.voice.repository.VoiceServiceException

import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer

import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest

import java.io.IOException
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
import github.ponyhuang.gimi.data.modelaccess.shared.encodePcm16Wav
import github.ponyhuang.gimi.data.modelaccess.shared.awaitSpeechRecognitionBody

/**
 * OpenAI 兼容的对话协议音频能力。
 *
 * 同一套 `/chat/completions` 端点承载两种能力，差异只在请求体与响应字段：
 * - 合成：请求带 `audio` 参数，响应在 `choices[].delta.audio.data` 增量下发 base64 音频；
 * - 识别：请求带 `input_audio` 内容块，响应在 `choices[].message.content` 返回文本。
 *
 * 请求形状严格跟随 OpenAI 官方契约（`audio` 只有 voice/format，`input_audio` 是
 * 裸 base64 + format 字段）；厂商自有契约的服务（MiMo 的 data-URI 编码与
 * `asr_options`）不在此协议里，由各自专属适配器承担，避免参数互相污染。
 *
 * 该协议被多个服务复用（含用户自建的 OpenAI 兼容代理），因此实现按服务标识集合注册，
 * 而不是绑定单一厂商。
 */
class OpenAiCompatibleChatProtocol(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson = Gson(),
) {
    /** 合成：以 SSE 逐行下发音频分块。 */
    fun synthesize(request: SynthesisRequest): Flow<ByteArray> = flow {
        val payload = JsonObject().apply {
            addProperty("model", request.modelId)
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "assistant", "content" to request.text))))
            add(
                "audio",
                JsonObject().apply {
                    addProperty("format", AUDIO_FORMAT_PCM16)
                    addProperty("voice", request.voiceId)
                },
            )
            addProperty("stream", true)
        }
        val httpRequest = Request.Builder()
            .url(request.baseUrl.trimEnd('/') + "/chat/completions")
            // OpenAI 及其兼容实现统一使用 Bearer 鉴权（api-key 是 Azure 风格，会被忽略导致 401），
            // 与同端点的识别路径保持一致
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
                        val bytes = runCatching { Base64.decode(audioData, Base64.DEFAULT) }
                            .getOrElse { throw IOException("语音合成返回了无效音频数据", it) }
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
                                // OpenAI 契约：裸 base64 + 独立 format 字段
                                // （MiMo 的 data-URI 形态由其专属协议承担）
                                "input_audio" to mapOf(
                                    "data" to Base64.encodeToString(wav, Base64.NO_WRAP),
                                    "format" to "wav",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val httpRequest = Request.Builder()
            .url(request.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${request.apiKey}")
            .addHeader("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val responseBody = okHttpClient.newBuilder().retryOnConnectionFailure(false).build()
            .newCall(httpRequest).awaitSpeechRecognitionBody()
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
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * OpenAI 兼容的语音合成能力。
 *
 * 服务标识集合覆盖使用该协议的服务（含用户自建的 OpenAI 兼容代理）。
 */
class OpenAiCompatibleSynthesizer @Inject constructor(
    private val protocol: OpenAiCompatibleChatProtocol,
) : SpeechSynthesizer {
    override val serviceIds: Set<String> = OPENAI_COMPATIBLE_AUDIO_SERVICE_IDS
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    override fun synthesize(request: SynthesisRequest): Flow<ByteArray> = protocol.synthesize(request)
}

/**
 * OpenAI 兼容的音频理解能力。
 *
 * 音频经对话协议交给模型理解，区别于专用转写服务的独立端点。
 */
class OpenAiCompatibleAudioRecognizer @Inject constructor(
    private val protocol: OpenAiCompatibleChatProtocol,
) : SpeechRecognizer {
    override val serviceIds: Set<String> = OPENAI_COMPATIBLE_AUDIO_SERVICE_IDS

    /**
     * 该协议同时承载专用转写（STT）与音频理解（ASR）两种语义：
     * 二者都是把音频交给对话模型，区别在模型自身是什么。
     */
    override val capabilities: Set<ModelCapability> =
        setOf(ModelCapability.STT, ModelCapability.ASR)

    override suspend fun recognize(request: RecognitionRequest): String = protocol.recognize(request)
}
