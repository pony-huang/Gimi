package github.ponyhuang.gimi.data.modelaccess.shared

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import github.ponyhuang.gimi.domain.voice.repository.VoiceAuthenticationException
import github.ponyhuang.gimi.domain.voice.repository.VoiceException
import github.ponyhuang.gimi.domain.voice.repository.VoiceHandshakeException
import github.ponyhuang.gimi.domain.voice.repository.VoiceRequestError
import github.ponyhuang.gimi.domain.voice.repository.VoiceServiceException

/** 语音 HTTP 服务常用的错误信封。非 JSON 网关响应仅保留状态码。 */
internal fun voiceHttpError(status: Int, body: String): VoiceRequestError? {
    val root = if (body.isBlank()) null else try {
        JsonParser.parseString(body) as? JsonObject
    } catch (_: JsonParseException) {
        null
    }
    val error = (root?.get("error") as? JsonObject) ?: (root?.get("base_resp") as? JsonObject) ?: root
    fun field(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        error?.get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
    }
    return VoiceRequestError.fromProvider(
        code = field("code", "status_code", "type"),
        message = field("message", "status_msg"),
        httpStatus = status,
    )
}

internal fun voiceHttpFailure(status: Int, body: String, message: String): VoiceException {
    val error = voiceHttpError(status, body)
    return if (status == 401 || status == 403) {
        VoiceAuthenticationException(message, requestError = error)
    } else {
        VoiceServiceException(message, requestError = error)
    }
}

/** WebSocket 握手的 HTTP 拒绝沿用同一个错误信封，完整响应摘要仅用于已有排查链路。 */
internal fun voiceHandshakeFailure(statusCode: Int, cause: Throwable, detail: String, body: String): Throwable =
    VoiceHandshakeException(
        statusCode,
        "语音服务握手失败（HTTP $statusCode）：$detail",
        requestError = voiceHttpError(statusCode, body),
        cause = cause,
    )
