package github.ponyhuang.gimi.data.modelaccess.minimax.speech

import javax.inject.Inject
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import github.ponyhuang.gimi.core.audio.VoiceAudioFormat
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest

import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.VoiceAuthenticationException
import github.ponyhuang.gimi.domain.voice.repository.VoiceException
import github.ponyhuang.gimi.domain.voice.repository.VoiceFailureKind
import github.ponyhuang.gimi.domain.voice.repository.VoiceServiceException
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer

import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer

import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest

import java.io.IOException
import java.io.Reader
import kotlinx.coroutines.CoroutineDispatcher
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
import okhttp3.Response
import okio.BufferedSource
import github.ponyhuang.gimi.data.modelaccess.shared.encodePcm16Wav
import github.ponyhuang.gimi.data.modelaccess.shared.awaitSpeechRecognitionBody

/** MiniMax 服务标识；与模型目录中的服务 ID 一致。 */
internal const val MINIMAX_SERVICE_ID = "minimax"

/**
 * MiniMax 语音协议实现。
 *
 * MiniMax 的两个语音能力分属两套互不相干的协议：合成是 SSE 文本流，识别是 multipart 文件上传。
 * 两者的端点路径由厂商固定，但域名跟随服务配置里同一个基址，因此三个语音端点（合成、识别、
 * 音色列表）始终指向同一区域，策略为 [EndpointStrategy.ConfiguredBaseUrl]。
 */
class MinimaxSpeechProtocol(
    private val httpClient: OkHttpClient,
    private val gson: Gson = Gson(),
    /**
     * 合成端点覆盖；为 `null` 时按请求携带的服务基址解析（[MiniMaxEndpoints.tts]）。
     * 只在测试里指向本地服务器时使用。
     */
    private val ttsEndpoint: String? = null,
    /**
     * 识别端点覆盖；为 `null` 时按请求携带的服务基址解析（[MiniMaxEndpoints.stt]）。
     * 只在测试里指向本地服务器时使用。
     */
    private val sttEndpoint: String? = null,
    /**
     * 运行流式响应解析器的调度器。可注入以便断言"先产出后报错"顺序的测试
     * 把流固定到同一线程，而不是在真实 IO 线程池上竞态。
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * 合成：`POST /v1/t2a_v2`，`stream: true`，产出 24 kHz 单声道 PCM16。
     *
     * 响应体支持两种分帧：文档给出的 JSON 数组 `[{...},{...}]`，以及以 `[DONE]` 结束的
     * SSE `data: {...}` 行。[parseChunks] 按首个非空白字节选择其一，并逐块产出。
     */
    fun synthesize(request: SynthesisRequest): Flow<ByteArray> = flow {
        val httpRequest = buildSynthesisRequest(request)
        // 付费的合成请求禁用 OkHttp 的连接失败隐式重试，避免请求体已缓冲时静默重发、重复计费；
        // 与识别路径（awaitSpeechRecognitionBody 侧已禁用）保持同一约定
        val call = httpClient.newBuilder().retryOnConnectionFailure(false).build().newCall(httpRequest)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                checkSuccessful(response)
                val source = response.body?.source() ?: throw IOException("语音合成响应为空")
                var emittedAudio = false
                parseChunks(source).collect { chunk ->
                    ensureBaseRespOk(chunk)
                    val audioBytes = chunkAudio(chunk)
                    if (audioBytes.isNotEmpty()) {
                        emittedAudio = true
                        emit(audioBytes)
                    }
                    if (chunkStatus(chunk) == 2) return@collect
                }
                if (!emittedAudio) throw IOException("语音合成未返回音频数据")
            }
        } finally {
            cancellation.dispose()
        }
    }.flowOn(dispatcher)

    /**
     * 识别：`POST /v1/speech_to_text`，multipart 上传 WAV 容器，返回最终文本。
     */
    suspend fun recognize(request: RecognitionRequest): String = withContext(Dispatchers.IO) {
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
            .addFormDataPart("stream", "false")
            .build()
        val httpRequest = Request.Builder()
            .url(sttEndpoint ?: MiniMaxEndpoints.stt(request.baseUrl))
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Accept", "application/json")
            .apply {
                // MiniMax 把缺省的 language 视作混合语种识别。
                if (request.language.isNotBlank() && request.language != "auto") {
                    header("language", request.language)
                }
            }
            .post(body)
            .build()
        val responseBody = httpClient.newBuilder().retryOnConnectionFailure(false).build()
            .newCall(httpRequest).awaitSpeechRecognitionBody(includeErrorBody = true)
        com.google.gson.JsonParser.parseString(responseBody)
            .asJsonObject
            .get("text")
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("MiniMax 语音识别未返回文本")
    }

    /**
     * 逐个产出流式响应中的 [JsonObject]。
     *
     * 网络读取错误或 JSON 解析错误向上抛出；取消自然传播，因为这里不捕获 [Throwable]。
     */
    internal fun parseChunks(source: BufferedSource): Flow<JsonObject> = flow {
        val peeked = source.peek()
        // 嗅探首个非空白、非 BOM 字节，在不消费 [source] 字节的前提下选择分帧方式。
        // 已缓冲字节全是空白/BOM 时继续 request 等待更多字节，而不是误判为空响应。
        var offset = 0L
        var firstByte: Byte? = null
        while (firstByte == null) {
            if (!peeked.request(offset + 1L)) break
            val buffer = peeked.buffer()
            val size = buffer.size
            while (offset < size) {
                val unsigned = buffer[offset].toInt() and 0xFF
                val isBom = size - offset >= 3 &&
                    unsigned == 0xEF &&
                    (buffer[offset + 1].toInt() and 0xFF) == 0xBB &&
                    (buffer[offset + 2].toInt() and 0xFF) == 0xBF
                val isWhitespace = unsigned == 0x20 || unsigned == 0x09 || unsigned == 0x0A || unsigned == 0x0D
                if (isBom || isWhitespace) {
                    offset += if (isBom) 3L else 1L
                } else {
                    firstByte = unsigned.toByte()
                    break
                }
            }
        }
        when (firstByte) {
            '['.code.toByte() -> {
                val reader: Reader = source.inputStream().reader(Charsets.UTF_8)
                JsonReader(reader).use { jsonReader ->
                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                        // JSON null 元素经 Gson 反序列化得到 Java null，直接 emit 会让下游 NPE。
                        // 显式类型参数：JsonReader 兼任 Reader，重载歧义会让 T 被推断成 Void
                        val element: JsonObject =
                            gson.fromJson<JsonObject>(jsonReader, JsonObject::class.java) ?: continue
                        emit(element)
                    }
                    jsonReader.endArray()
                }
            }
            null -> throw IOException("语音合成响应为空")
            else -> {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val value = if (trimmed.startsWith("data:")) trimmed.removePrefix("data:").trim() else trimmed
                    if (value == "[DONE]" || value == "data: [DONE]") break
                    val element = gson.fromJson<JsonObject>(value, JsonObject::class.java) ?: continue
                    emit(element)
                }
            }
        }
    }

    private fun buildSynthesisRequest(request: SynthesisRequest): Request {
        val payload = JsonObject().apply {
            addProperty("model", request.modelId)
            addProperty("text", request.text)
            addProperty("stream", true)
            add(
                "voice_setting",
                JsonObject().apply {
                    addProperty("voice_id", request.voiceId)
                    addProperty("speed", 1.0)
                    addProperty("vol", 1.0)
                    addProperty("pitch", 0)
                },
            )
            add(
                "audio_setting",
                JsonObject().apply {
                    addProperty("sample_rate", VoiceAudioFormat.OUTPUT_SAMPLE_RATE_HZ)
                    addProperty("bitrate", 128000)
                    addProperty("format", "pcm")
                    addProperty("channel", VoiceAudioFormat.CHANNEL_COUNT)
                },
            )
            addProperty("language_boost", "auto")
            addProperty("subtitle_enable", false)
            add(
                "stream_options",
                JsonObject().apply {
                    addProperty("exclude_aggregated_audio", true)
                },
            )
        }
        return Request.Builder()
            .url(ttsEndpoint ?: MiniMaxEndpoints.tts(request.baseUrl))
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Accept", "text/event-stream")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun checkSuccessful(response: Response) {
        if (response.isSuccessful) return
        val detail = response.body?.string()?.take(500).orEmpty()
        val message = "语音合成失败（HTTP ${response.code}）" +
            detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
        // 401/403 是密钥问题，与服务端业务错误、网络故障是三类完全不同的用户问题。
        if (response.code == 401 || response.code == 403) {
            throw VoiceAuthenticationException(message)
        }
        throw VoiceServiceException(message)
    }

    /**
     * 把 `base_resp.status_code != 0` 暴露为 [TtsSynthesisException]，使播放层能把
     * 部分播放失败归因到具体的厂商错误码。错误在解码音频之前检查，因此失败形态与
     * 出错分块是否带音频无关。
     */
    private fun ensureBaseRespOk(chunk: JsonObject) {
        val base = chunk.getAsJsonObject("base_resp") ?: return
        // 服务端可能下发显式 null：Gson 返回 JsonNull 而非 Java null，
        // 直接 asString/asInt 会抛异常杀掉整条音频流，必须先挡掉
        val statusCode = base.get("status_code")?.takeUnless { it.isJsonNull }?.asInt ?: 0
        if (statusCode == 0) return
        val statusMessage = base.get("status_msg")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        throw TtsSynthesisException(statusCode, baseRespMessage(statusCode), statusMessage)
    }

    private fun chunkAudio(chunk: JsonObject): ByteArray {
        val hex = chunk.getAsJsonObject("data")?.get("audio")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        if (hex.isEmpty()) return ByteArray(0)
        return decodeAudioHex(hex)
    }

    /** 返回 `data.status`（缺失或为 null 时按 1 处理，使流继续）。 */
    private fun chunkStatus(chunk: JsonObject): Int =
        chunk.getAsJsonObject("data")?.get("status")?.takeUnless { it.isJsonNull }?.asInt ?: 1

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}

/**
 * MiniMax 语音合成能力。
 *
 * 端点路径由厂商固定，域名跟随服务配置里同一个基址，与识别、音色列表保持同一区域。
 */
class MinimaxSynthesizer @Inject constructor(
    private val protocol: MinimaxSpeechProtocol,
) : SpeechSynthesizer {
    override val serviceIds: Set<String> = setOf(MINIMAX_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    /** 端点跟随服务配置的基址，用户改一次接入地址即可整体切换区域。 */
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.ConfiguredBaseUrl

    override fun synthesize(request: SynthesisRequest): Flow<ByteArray> = protocol.synthesize(request)
}

/**
 * MiniMax 语音识别能力。
 *
 * 走 multipart 上传端点，与合成分属两套协议；端点域名与合成、音色列表同源。
 */
class MinimaxRecognizer @Inject constructor(
    private val protocol: MinimaxSpeechProtocol,
) : SpeechRecognizer {
    override val serviceIds: Set<String> = setOf(MINIMAX_SERVICE_ID)
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.STT)

    /** 端点跟随服务配置的基址，用户改一次接入地址即可整体切换区域。 */
    override val endpointStrategy: EndpointStrategy = EndpointStrategy.ConfiguredBaseUrl

    override suspend fun recognize(request: RecognitionRequest): String = protocol.recognize(request)
}

/**
 * 厂商特定的合成失败。
 *
 * 继承 [VoiceException]，因此捕获 IO 失败的上层调用方与既有网关测试的行为保持不变，
 * 同时失败分类固化在异常上，界面无需解析错误文案。
 */
internal class TtsSynthesisException(
    val statusCode: Int,
    localizedMessage: String,
    val statusMessage: String,
) : VoiceException(
    VoiceFailureKind.Service,
    buildMessage(localizedMessage, statusMessage),
) {
    private companion object {
        fun buildMessage(localized: String, serverMessage: String): String {
            val core = "语音合成失败（$localized）"
            return if (serverMessage.isBlank()) core else "$core$serverMessage"
        }
    }
}
