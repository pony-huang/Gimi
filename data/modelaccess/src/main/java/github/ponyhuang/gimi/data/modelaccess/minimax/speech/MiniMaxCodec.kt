package github.ponyhuang.gimi.data.modelaccess.minimax.speech

import java.io.IOException

/**
 * MiniMax 语音协议共用的负载解码。
 *
 * 合成接口把音频以十六进制字符串放在 JSON 里，HTTP 与两种 WebSocket 形态共用同一套解码。
 */

/** 把厂商返回的十六进制音频解码成字节；非法字符是协议错误而不是可忽略的噪声。 */
internal fun hexToBytes(hex: String): ByteArray {
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

/** 厂商错误码到简短本地化短语的映射；界面据此区分鉴权、限流、参数与服务故障。 */
internal fun baseRespMessage(code: Int): String = when (code) {
    1004 -> "鉴权失败"
    1002, 1039 -> "请求过于频繁"
    1001 -> "服务超时"
    1042 -> "输入超限"
    2013 -> "参数错误"
    1000 -> "未知错误"
    else -> "错误码 $code"
}

/** 把厂商返回的十六进制音频解码成字节，非法负载是协议错误。 */
internal fun decodeAudioHex(hex: String): ByteArray =
    runCatching { hexToBytes(hex) }
        .getOrElse { throw IOException("语音合成返回了无效音频数据", it) }
