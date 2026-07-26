package github.ponyhuang.asssistantai.data.speech.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
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
 * [android.media.AudioTrack] configuration. The streaming response may be framed either
 * as a JSON array of chunks (the documented example) or as SSE `data: {...}` lines;
 * [parseChunks] handles both before yielding any audio.
 */
class MinimaxTtsGateway(
    private val httpClient: OkHttpClient,
    private val gson: Gson = Gson(),
    private val endpoint: String = DEFAULT_ENDPOINT,
) : SpeechSynthesisGateway {

    override fun synthesize(config: SpeechSynthesisConfig, text: String): Flow<ByteArray> = flow {
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
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = httpClient.newCall(request)
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string()?.take(500).orEmpty()
                    throw IOException(
                        "语音合成失败（HTTP ${response.code}）" +
                            detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty(),
                    )
                }
                val body = response.body?.string() ?: throw IOException("语音合成响应为空")
                val chunks = parseChunks(body)
                if (chunks.isEmpty()) throw IOException("语音合成响应内容无法解析")
                var emittedAudio = false
                for (chunk in chunks) {
                    val baseStatus = chunk
                        .getAsJsonObject("base_resp")
                        ?.get("status_code")
                        ?.asInt
                        ?: 0
                    if (baseStatus != 0) {
                        val message = chunk
                            .getAsJsonObject("base_resp")
                            ?.get("status_msg")
                            ?.asString
                            .orEmpty()
                        throw IOException("语音合成失败（${baseRespMessage(baseStatus)}）$message")
                    }
                    val audioHex = chunk
                        .getAsJsonObject("data")
                        ?.get("audio")
                        ?.asString
                        .orEmpty()
                    if (audioHex.isNotEmpty()) {
                        val bytes = runCatching { hexToBytes(audioHex) }
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

    /**
     * The streaming body comes in two observed shapes:
     * 1. A JSON array of chunks — single line `[{...},{...}]` or pretty-printed multi-line.
     * 2. SSE-style `data: {...}` lines separated by blank lines.
     * Try the array first (the documented example), fall back to per-line SSE parsing
     * for compatibility with either framing.
     */
    internal fun parseChunks(body: String): List<JsonObject> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        runCatching { gson.fromJson(trimmed, JsonArray::class.java) }
            .getOrNull()
            ?.takeIf { it.size() > 0 }
            ?.filterIsInstance<JsonObject>()
            ?.let { return it }
        return trimmed.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                val payload = if (line.startsWith("data:")) {
                    line.removePrefix("data:").trim()
                } else {
                    line
                }
                if (payload.isEmpty()) return@mapNotNull null
                runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull()
            }
            .toList()
    }

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