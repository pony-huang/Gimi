package github.ponyhuang.gimi.data.modelaccess.deepseek.speech

import github.ponyhuang.gimi.data.modelaccess.shared.awaitSpeechRecognitionBody
import github.ponyhuang.gimi.data.modelaccess.shared.encodePcm16Wav
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** DeepSeek 独立语音端点的服务标识；与模型目录中的服务 ID 一致。 */
internal const val DEEPSEEK_SPEECH_SERVICE_ID = "deepseek"

/**
 * DeepSeek 独立语音端点解析。
 *
 * 识别（`/v1/tools/asr`）与合成（`/v1/tools/tts`）挂在同一 API 域的一个基址下，且请求
 * 不携带模型参数：端点是固定的工具接口，模板目录里的模型 ID 只是选择器占位，不参与请求。
 * 生产链路的基址由模板写死为官方地址（厂商自有、不可自定义）；测试直接传入 MockWebServer
 * 地址覆盖。基址为空或不是 HTTP(S) 地址时回落官方默认地址。
 */
internal object DeepSeekSpeechEndpoints {
    /** 官方唯一基址；与模型目录模板中的语音连接端点同源。 */
    const val DEFAULT_BASE_URL = "https://api.deepseek.com"

    /** 语音识别端点路径。 */
    const val ASR_PATH = "/v1/tools/asr"

    /** 语音合成端点路径。 */
    const val TTS_PATH = "/v1/tools/tts"

    /** 语音识别端点。 */
    fun asr(baseUrl: String): String = resolve(baseUrl, ASR_PATH)

    /** 语音合成端点。 */
    fun tts(baseUrl: String): String = resolve(baseUrl, TTS_PATH)

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
 * DeepSeek 独立语音识别端点。
 *
 * `POST {baseUrl}/v1/tools/asr`，multipart 上传整段 PCM 的 WAV 容器，响应为
 * `{"text": ...}`。请求不带模型与语种参数：21 种语言由服务端自动判定。音频限制为
 * 16 位 PCM WAV、单次最长 120 秒、最大 8 MiB；应用录音（16 kHz 单声道 PCM16）天然在
 * 限制内，超限由服务端拒绝并按失败透出，不做本地截断——整段重传同样计费。
 */
class DeepSeekSpeechRecognizer @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SpeechRecognizer {
    override val serviceIds: Set<String> = setOf(DEEPSEEK_SPEECH_SERVICE_ID)

    /** 只承担专用转写（STT）；DeepSeek 对话协议不支持音频理解。 */
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.STT)

    // 端点由厂商自有：官方地址写死，不跟随用户配置
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.VendorOwned

    override suspend fun recognize(request: RecognitionRequest): String = withContext(Dispatchers.IO) {
        val wav = encodePcm16Wav(
            pcm16 = request.pcm16,
            sampleRateHz = request.sampleRateHz,
            channelCount = request.channelCount,
        )
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "recording.wav", wav.toRequestBody(WAV_MEDIA_TYPE))
            .build()
        val httpRequest = Request.Builder()
            .url(DeepSeekSpeechEndpoints.asr(request.baseUrl))
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Accept", "application/json")
            .post(body)
            .build()
        val responseBody = okHttpClient.newBuilder().retryOnConnectionFailure(false).build()
            .newCall(httpRequest).awaitSpeechRecognitionBody(includeErrorBody = true)
        Json.parseToJsonElement(responseBody)
            .jsonObject["text"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("DeepSeek 语音识别未返回文本")
    }

    private companion object {
        val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}

/**
 * DeepSeek 独立语音合成端点。
 *
 * `POST {baseUrl}/v1/tools/tts`，请求体声明 `format=pcm`：返回 24 kHz 单声道 PCM16
 * 裸流（无容器头），与本地播放器期望的格式一致，响应体可边接收边播放。
 * 文本上限 10,000 计费字符；调用方按句切分送入，远小于该上限。
 */
class DeepSeekSpeechSynthesizer @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SpeechSynthesizer {
    override val serviceIds: Set<String> = setOf(DEEPSEEK_SPEECH_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    // 端点由厂商自有：官方地址写死，不跟随用户配置
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.VendorOwned

    override fun synthesize(request: SynthesisRequest): Flow<ByteArray> = flow {
        val payload = buildJsonObject {
            put("text", request.text)
            // 调用方未选音色时回落到官方默认音色，而不是把空值发给服务端
            put("voice_id", request.voiceId.ifBlank { DEFAULT_VOICE_ID })
            put("format", RESPONSE_FORMAT_PCM)
        }
        val httpRequest = Request.Builder()
            .url(DeepSeekSpeechEndpoints.tts(request.baseUrl))
            .addHeader("Authorization", "Bearer ${request.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
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
        const val DEFAULT_VOICE_ID = "stella"
        // 4096 字节 ≈ 85 ms @24 kHz PCM16；块越小首帧到耳越快，越大单次写入开销越低
        const val READ_CHUNK_BYTES: Long = 4096
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
