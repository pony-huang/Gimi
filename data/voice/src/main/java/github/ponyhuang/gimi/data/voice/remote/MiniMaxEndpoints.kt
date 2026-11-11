package github.ponyhuang.gimi.data.voice.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * MiniMax 三个语音端点的统一解析。
 *
 * MiniMax 的合成（`/v1/t2a_v2`）、识别（`/v1/speech_to_text`）与音色列表（`/v1/get_voice`）
 * 是同一套 API 的三个入口，域名只取决于账号所在区域：国内与国际各有一套入口。
 * 早期实现把三个端点分别写死在两个域名上，于是国内账号的合成请求会打到国际站点，
 * 表现为"密钥有效但合成永远失败"。
 *
 * 现在三个端点都从**同一个服务基址**派生：用户在服务详情里填的接入地址决定区域与代理，
 * 三个端点随之保持一致。基址为空或不是 HTTP(S) 地址时回落到国际站默认地址。
 */
internal object MiniMaxEndpoints {
    /** 国际站默认基址；与服务目录里 MiniMax 服务的默认接入地址一致。 */
    const val DEFAULT_BASE_URL = "https://api.minimaxi.com/v1"

    /** 语音合成端点路径。 */
    const val TTS_PATH = "/t2a_v2"

    /** 语音识别端点路径。 */
    const val STT_PATH = "/speech_to_text"

    /** 音色列表端点路径。 */
    const val VOICE_PATH = "/get_voice"

    /** 双向流式语音合成端点路径。 */
    const val WS_BIDI_PATH = "/ws/v1/t2a_v2_bidi"

    /** 双向流式语音合成的默认端点；与 [DEFAULT_BASE_URL] 同区域。 */
    const val DEFAULT_WS_BIDI = "wss://api.minimaxi.com$WS_BIDI_PATH"

    /** 语音合成端点。 */
    fun tts(baseUrl: String): String = resolve(baseUrl, TTS_PATH)

    /** 语音识别端点。 */
    fun stt(baseUrl: String): String = resolve(baseUrl, STT_PATH)

    /** 音色列表端点。 */
    fun voice(baseUrl: String): String = resolve(baseUrl, VOICE_PATH)

    /**
     * 双向流式语音合成端点。
     *
     * WebSocket 的路径与 HTTP 端点不同：HTTP 是 `<基址>/t2a_v2`，WS 是 `<主机>/ws/v1/t2a_v2_bidi`，
     * 因此不能沿用 [resolve] 的路径追加——基址里的 `/v1` 在 WS 路径里另有位置。
     * 基址末段是 `/v1` 时把它替换掉，前面的路径前缀（自建代理或子路径部署）保留；
     * 没有 `/v1` 时直接追加完整 WS 路径。协议与主机跟随服务基址，
     * 因此用户切换区域或代理时合成端点随之切换。
     *
     * 这里按字符串改写而不是用 `HttpUrl` 重建：`HttpUrl` 不接受 ws/wss 协议，
     * 而主机部分必须原样保留（自建代理可能把账号密码写在 URL 里）。
     */
    fun wsBidi(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.toHttpUrlOrNull() == null) return DEFAULT_WS_BIDI
        val scheme = when (trimmed.substringBefore("://").lowercase()) {
            "https" -> "wss"
            "http" -> "ws"
            else -> return DEFAULT_WS_BIDI
        }
        val afterScheme = trimmed.substringAfter("://")
        val authority = afterScheme.substringBefore('/')
        val rawPath = afterScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
        // 统一成以 / 开头的路径，才能把末段 /v1 整体换掉。
        val path = if (rawPath.isEmpty()) "" else "/${rawPath.trimStart('/')}"
        val prefix = path.trimEnd('/').removeSuffix("/v1").trimEnd('/')
        return "$scheme://$authority$prefix$WS_BIDI_PATH"
    }

    /**
     * 把服务基址解析成具体端点。
     *
     * 基址为空或不是 HTTP(S) 地址时不参与请求构造，改用厂商默认地址：
     * 拼出非法 URL 只会得到一个难以理解的失败。
     */
    private fun resolve(baseUrl: String, path: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "$trimmed$path"
        } else {
            "$DEFAULT_BASE_URL$path"
        }
    }
}
