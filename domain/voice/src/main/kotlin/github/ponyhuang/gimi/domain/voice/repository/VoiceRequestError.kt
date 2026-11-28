package github.ponyhuang.gimi.domain.voice.repository

/**
 * 可显示的语音服务错误。HTTP 状态与服务商错误码分别保留，部分协议只提供后者。
 * @property message 经清理并限制长度的服务商摘要。
 */
data class VoiceRequestError private constructor(
    val httpStatus: Int?,
    val code: String?,
    val message: String?,
) {
    companion object {
        private const val MAX_MESSAGE_LENGTH = 88
        private val codePattern = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}")
        private val sensitive = Regex(
            """(?i)\b(?:authorization|bearer|cookie|api[-_ ]?key|access[-_ ]?token|password|prompt|input_text)\b|\b(?:sk[-_]|eyJ)[\w-]+|https?://|[\p{Cc}\p{Cf}]""",
        )

        fun fromProvider(
            code: String? = null,
            message: String? = null,
            httpStatus: Int? = null,
        ): VoiceRequestError? {
            val status = httpStatus?.takeIf { it in 400..599 }
            val safeCode = code?.trim()?.takeIf { codePattern.matches(it) && !sensitive.containsMatchIn(it) }
            // 只接收协议的错误摘要。含请求字段、凭据或结构化正文时省去原文，仍保留诊断码。
            val safeMessage = message?.trim()?.takeIf {
                it.isNotEmpty() && !sensitive.containsMatchIn(it) && it.none { char -> char in "{}[]\\\"'‘’“”＂「」" }
            }?.replace(Regex("""\s+"""), " ")?.let {
                if (it.length <= MAX_MESSAGE_LENGTH) it else {
                    val prefix = it.take(MAX_MESSAGE_LENGTH).trimEnd()
                    prefix.dropLast(if (prefix.lastOrNull()?.isHighSurrogate() == true) 1 else 0) + "…"
                }
            }
            if (status == null && safeCode == null && safeMessage == null) return null
            return VoiceRequestError(status, safeCode, safeMessage)
        }
    }
}

/** 从语音后端的分类包装中读取服务商详情，丢弃本地异常的原始 message。 */
fun Throwable.voiceRequestError(): VoiceRequestError? {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        val error = when (current) {
            is VoiceAudioBackendException -> current.requestError
            is VoiceException -> current.requestError
            else -> null
        }
        if (error != null) return error
        current = current.cause
    }
    return null
}
