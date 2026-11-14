package github.ponyhuang.gimi.data.modelaccess.openai.speech

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import github.ponyhuang.gimi.data.modelaccess.shared.encodePcm16Wav
import github.ponyhuang.gimi.data.modelaccess.shared.awaitSpeechRecognitionBody

/** OpenAI 专用语音端点的服务标识；与模型目录中的服务 ID 一致。 */
internal const val OPENAI_SPEECH_SERVICE_ID = "openai"

/**
 * OpenAI 专用语音端点解析。
 *
 * 合成（`/audio/speech`）与识别（`/audio/transcriptions`）挂在同一套 API 的一个基址下。
 * 生产链路的基址由 [speechRequestBaseUrl] 解析为官方唯一地址（厂商自有、不可自定义）；
 * 测试直接传入 MockWebServer 地址覆盖。基址为空或不是 HTTP(S) 地址时回落官方默认地址。
 */
internal object OpenAiSpeechEndpoints {
    /** 官方唯一基址；与 [SpeechEndpoints.OPENAI_SPEECH_BASE_URL] 同源。 */
    const val DEFAULT_BASE_URL = SpeechEndpoints.OPENAI_SPEECH_BASE_URL

    /** 语音合成端点路径。 */
    const val TTS_PATH = "/audio/speech"

    /** 语音识别端点路径。 */
    const val STT_PATH = "/audio/transcriptions"

    /** 语音合成端点。 */
    fun tts(baseUrl: String): String = resolve(baseUrl, TTS_PATH)

    /** 语音识别端点。 */
    fun stt(baseUrl: String): String = resolve(baseUrl, STT_PATH)

    private fun resolve(baseUrl: String, path: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "$trimmed$path"
        } else {
            "$DEFAULT_BASE_URL$path"
        }
    }
}

/**
 * OpenAI 专用转写端点的语音识别。
 *
 * `POST {baseUrl}/audio/transcriptions`，multipart 上传整段 PCM 的 WAV 容器，
 * `response_format=json`，响应为 `{"text": ...}`。区别于对话协议的 `input_audio`
 * （音频理解）：这里是专用转写服务，模型与密钥独立于聊天服务配置。
 */
class OpenAiSpeechRecognizer @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SpeechRecognizer {
    override val serviceIds: Set<String> = setOf(OPENAI_SPEECH_SERVICE_ID)

    /** 只承担专用转写（STT）；对话协议的音频理解（ASR）由 OpenAiCompatibleAudioRecognizer 承担。 */
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.STT)

    // 端点由厂商自有：官方唯一基址写死，不跟随用户配置
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.VendorOwned

    override suspend fun recognize(request: RecognitionRequest): String = withContext(Dispatchers.IO) {
        val wav = encodePcm16Wav(
            pcm16 = request.pcm16,
            sampleRateHz = request.sampleRateHz,
            channelCount = request.channelCount,
        )
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", request.modelId)
            .addFormDataPart("file", "recording.wav", wav.toRequestBody(WAV_MEDIA_TYPE))
            .addFormDataPart("response_format", "json")
            .apply {
                // OpenAI 要求 ISO-639-1 语种；auto 表示由服务自行判定，缺省即混合语种
                if (request.language.isNotBlank() && request.language != "auto") {
                    addFormDataPart("language", request.language)
                }
            }
            .build()
        val httpRequest = Request.Builder()
            .url(OpenAiSpeechEndpoints.stt(request.baseUrl))
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Accept", "application/json")
            .post(body)
            .build()
        val responseBody = okHttpClient.newBuilder().retryOnConnectionFailure(false).build()
            .newCall(httpRequest).awaitSpeechRecognitionBody(includeErrorBody = true)
        JsonParser.parseString(responseBody)
            .asJsonObject
            .get("text")
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("OpenAI 语音识别未返回文本")
    }

    private companion object {
        val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}

/**
 * OpenAI 专用语音合成端点。
 *
 * `POST {baseUrl}/audio/speech`，请求体声明 `response_format=pcm`：返回 24 kHz 单声道
 * PCM16 裸流（无容器头），与本地播放器期望的格式一致，响应体可边接收边播放。
 * `input` 上限 4096 字符；调用方按句切分送入，远小于该上限。
 */
class OpenAiSpeechSynthesizer @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SpeechSynthesizer {
    override val serviceIds: Set<String> = setOf(OPENAI_SPEECH_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    // 端点由厂商自有：官方唯一基址写死，不跟随用户配置
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.VendorOwned

    // 全图没有 Gson 绑定；voice 链路的其它 Gson 消费者都在内部自建实例，这里保持一致
    private val gson = Gson()

    override fun synthesize(request: SynthesisRequest): Flow<ByteArray> = flow {
        val payload = JsonObject().apply {
            addProperty("model", request.modelId)
            addProperty("input", request.text)
            // voice 必填；调用方未选音色时回落到官方默认音色，而不是让请求直接 400
            addProperty("voice", request.voiceId.ifBlank { DEFAULT_VOICE_ID })
            // pcm = 24 kHz 单声道 PCM16 裸流，与 VoiceAudioFormat.OUTPUT_SAMPLE_RATE_HZ 对齐；
            // stream_format 保持默认 audio（原始二进制），无需 SSE 分帧
            addProperty("response_format", RESPONSE_FORMAT_PCM)
        }
        val httpRequest = Request.Builder()
            .url(OpenAiSpeechEndpoints.tts(request.baseUrl))
            .addHeader("Authorization", "Bearer ${request.apiKey}")
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
                    // 先确保至少 1 字节到达再读当前已缓冲的数据：readByteArray(n) 在流尾
                    // 不足 n 字节时抛 EOF 而不是返回短块，因此块大小取已到达的字节数，
                    // 顺带让网络分块尽早产出而不是等满一块
                    source.request(1)
                    val chunk = source.readByteArray(minOf(READ_CHUNK_BYTES, source.buffer.size))
                    if (chunk.isNotEmpty()) {
                        emittedAudio = true
                        emit(chunk)
                    }
                }
                if (!emittedAudio) throw IOException("语音合成未返回音频数据")
            }
        } finally {
            cancellation.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val RESPONSE_FORMAT_PCM = "pcm"
        const val DEFAULT_VOICE_ID = "alloy"
        // 4096 字节 ≈ 85 ms @24 kHz PCM16；块越小首帧到耳越快，越大单次写入开销越低
        const val READ_CHUNK_BYTES: Long = 4096
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
