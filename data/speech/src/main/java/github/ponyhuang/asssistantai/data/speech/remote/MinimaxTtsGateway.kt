package github.ponyhuang.asssistantai.data.speech.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import java.io.IOException
import java.io.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource

/**
 * Talks to Minimax Text-to-Audio v2 (`POST https://api.minimaxi.com/v1/t2a_v2`) with
 * `stream: true` and yields PCM chunks ready for
 * [github.ponyhuang.asssistantai.data.speech.playback.AndroidSpeechPlaybackRepository].
 *
 * The endpoint is hardcoded on purpose: the URL is owned by the vendor, not derived
 * from the configurable chat `apiBaseUrl`, so there is no scenario where this
 * gateway should target a different host. The constructor still accepts an
 * [endpoint] override so unit tests can point it at [okhttp3.mockwebserver.MockWebServer].
 *
 * Audio format is requested as 24 kHz mono PCM16 to match the playback repo's hard-coded
 * [android.media.AudioTrack] configuration. The streaming response is read incrementally
 * off [BufferedSource] so the first hex chunk reaches the playback layer as soon as the
 * server flushes it, rather than after the entire response has been buffered in memory.
 * Two framings are accepted: a JSON array `[{...},{...}]` (the documented shape) or
 * SSE `data: {...}` lines terminated by `[DONE]`. [parseChunks] picks one based on the
 * first non-whitespace byte and yields each parsed [JsonObject] as it appears.
 */
class MinimaxTtsGateway(
    private val httpClient: OkHttpClient,
    private val gson: Gson = Gson(),
    private val endpoint: String = DEFAULT_ENDPOINT,
) : SpeechSynthesisGateway {

    override fun synthesize(config: SpeechSynthesisConfig, text: String): Flow<ByteArray> = flow {
        val request = buildRequest(config, text)
        val call = httpClient.newCall(request)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                checkSuccessful(response)
                val source = response.body?.source()
                    ?: throw IOException("语音合成响应为空")
                var emittedAudio = false
                parseChunks(source).collect { chunk ->
                    ensureBaseRespOk(chunk)
                    val audioBytes = decodeAudioHex(chunk)
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
    }.flowOn(Dispatchers.IO)

    /**
     * Reads the streaming response one [JsonObject] at a time. The first non-whitespace
     * byte chooses between two framings:
     *  - `'['` opens a JSON array parsed incrementally by [JsonReader.hasNext], so each
     *    chunk can be emitted as soon as it is parsed.
     *  - anything else falls back to per-line SSE parsing terminated by `[DONE]`.
     *
     * Network read errors or malformed JSON bubble up as [IOException] / parse errors;
     * cancellation propagates naturally because we don't catch [Throwable].
     */
    internal fun parseChunks(source: BufferedSource): Flow<JsonObject> = flow {
        val peeked = source.peek()
        if (!peeked.request(1L)) throw IOException("语音合成响应为空")
        val buffer = peeked.buffer()
        // Sniff the first non-whitespace, non-BOM byte to route between the JSON-array
        // and SSE framings without consuming any bytes from [source].
        var offset = 0L
        var firstByte: Byte? = null
        val size = buffer.size
        while (offset < size) {
            val unsigned = buffer[offset].toInt() and 0xFF
            val isBom = size - offset >= 3 &&
                unsigned == 0xEF &&
                (buffer[offset + 1].toInt() and 0xFF) == 0xBB &&
                (buffer[offset + 2].toInt() and 0xFF) == 0xBF
            val isWhitespace = unsigned == 0x20 || unsigned == 0x09 ||
                unsigned == 0x0A || unsigned == 0x0D
            if (isBom) {
                offset += 3
                continue
            }
            if (!isWhitespace) {
                firstByte = buffer[offset]
                break
            }
            offset++
        }
        val first = firstByte ?: throw IOException("语音合成响应内容无法解析")
        if (first == '['.code.toByte()) {
            val reader: Reader = source.inputStream().reader(Charsets.UTF_8)
            JsonReader(reader).use { jsonReader ->
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    emit(gson.fromJson(jsonReader, JsonObject::class.java))
                }
                jsonReader.endArray()
            }
        } else {
            while (true) {
                val line = source.readUtf8Line() ?: break
                val value = line.trim()
                if (value.isEmpty()) continue
                if (value == "[DONE]" || value == "data: [DONE]") break
                val payload = if (value.startsWith("data:")) {
                    value.removePrefix("data:").trim()
                } else {
                    value
                }
                if (payload.isEmpty()) continue
                emit(gson.fromJson(payload, JsonObject::class.java))
            }
        }
    }

    /**
     * Returns the first byte in [BufferedSource.peek]'s buffer that is not
     * whitespace or a leading UTF-8 BOM, or `null` if the peeked buffer is empty.
     * Used to route the streaming body between JSON-array and SSE framings
     * without consuming any bytes from [source].
     */
    /**
     * Returns the first non-whitespace, non-BOM byte of the body without
     * consuming any bytes from [source]. Uses [BufferedSource.peek] which
     * returns a sibling [BufferedSource] backed by the same buffer; reading
     * from the sibling does not advance [source].
     */
    private fun okio.BufferedSource.firstNonWhitespaceByte(): Byte? {
        val peeked = peek()
        val buffer = peeked.buffer()
        val size = buffer.size
        if (size == 0L) return null
        var offset = 0L
        while (offset < size) {
            val unsigned = buffer[offset].toInt() and 0xFF
            val isBom = size - offset >= 3 &&
                unsigned == 0xEF &&
                (buffer[offset + 1].toInt() and 0xFF) == 0xBB &&
                (buffer[offset + 2].toInt() and 0xFF) == 0xBF
            val isWhitespace = unsigned == 0x20 || unsigned == 0x09 ||
                unsigned == 0x0A || unsigned == 0x0D
            if (isBom) {
                offset += 3
                continue
            }
            if (!isWhitespace) return buffer[offset]
            offset++
        }
        return null
    }

    private fun buildRequest(config: SpeechSynthesisConfig, text: String): Request {
        val payload = JsonObject().apply {
            addProperty("model", config.modelId)
            addProperty("text", text)
            addProperty("stream", true)
            add(
                "voice_setting",
                JsonObject().apply {
                    addProperty("voice_id", config.voiceId)
                    addProperty("speed", 1.0)
                    addProperty("vol", 1.0)
                    addProperty("pitch", 0)
                },
            )
            add(
                "audio_setting",
                JsonObject().apply {
                    addProperty("sample_rate", 24000)
                    addProperty("bitrate", 128000)
                    addProperty("format", "pcm")
                    addProperty("channel", 1)
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
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun checkSuccessful(response: Response) {
        if (response.isSuccessful) return
        val detail = response.body?.string()?.take(500).orEmpty()
        throw IOException(
            "语音合成失败（HTTP ${response.code}）" +
                detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty(),
        )
    }

    /**
     * Surfaces [base_resp.status_code] failures as [TtsSynthesisException] so the
     * downstream player / repository can attribute partial-playback failures back to
     * a specific vendor error code. Called before audio is decoded so the failure
     * mode is consistent regardless of whether the offending chunk carries audio.
     */
    private fun ensureBaseRespOk(chunk: JsonObject) {
        val base = chunk.getAsJsonObject("base_resp") ?: return
        val statusCode = base.get("status_code")?.asInt ?: 0
        if (statusCode == 0) return
        val statusMessage = base.get("status_msg")?.asString.orEmpty()
        throw TtsSynthesisException(statusCode, baseRespMessage(statusCode), statusMessage)
    }

    private fun decodeAudioHex(chunk: JsonObject): ByteArray {
        val hex = chunk.getAsJsonObject("data")?.get("audio")?.asString.orEmpty()
        if (hex.isEmpty()) return ByteArray(0)
        return runCatching { hexToBytes(hex) }
            .getOrElse { throw IOException("语音合成返回了无效音频数据", it) }
    }

    /** Returns `data.status` (defaulting to 1 when missing so streaming continues). */
    private fun chunkStatus(chunk: JsonObject): Int =
        chunk.getAsJsonObject("data")?.get("status")?.asInt ?: 1

    private fun baseRespMessage(code: Int): String = when (code) {
        1004 -> "鉴权失败"
        1002, 1039 -> "请求过于频繁"
        1001 -> "服务超时"
        1042 -> "输入超限"
        2013 -> "参数错误"
        1000 -> "未知错误"
        else -> "错误码 $code"
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must be even length" }
        val length = hex.length / 2
        val out = ByteArray(length)
        var i = 0
        while (i < length) {
            val high = Character.digit(hex[i * 2], 16)
            val low = Character.digit(hex[i * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "Invalid hex character at $i" }
            out[i] = ((high shl 4) or low).toByte()
            i++
        }
        return out
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://api.minimaxi.com/v1/t2a_v2"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Vendor-specific TTS failure surfaced when [MinimaxTtsGateway] observes
 * `base_resp.status_code != 0` in any chunk of a streaming response. Inherits
 * [IOException] so callers that catch IO failures (including existing gateway tests
 * that assert [IOException::class.java]) keep working unchanged.
 */
internal class TtsSynthesisException(
    val statusCode: Int,
    localizedMessage: String,
    val statusMessage: String,
) : IOException(
    buildMessage(localizedMessage, statusMessage),
) {
    private companion object {
        fun buildMessage(localized: String, serverMessage: String): String {
            val core = "语音合成失败（$localized）"
            return if (serverMessage.isBlank()) core else "$core$serverMessage"
        }
    }
}