package github.ponyhuang.gimi.domain.speech.model

/** OpenClaw 式前台语音唤醒的运行阶段。 */
enum class VoiceWakeStatus {
    Disabled,
    Unavailable,
    Idle,
    Listening,
    Submitting,
    Error,
}

/** 唤醒短语未通过本地规则的原因。 */
enum class WakePhraseError { InvalidLength, InvalidCharacters, Duplicate, LastPhrase }

/** 携带类型化短语错误，供展示层映射本地化文案。 */
class WakePhraseException(val error: WakePhraseError) : IllegalArgumentException(error.name)

/**
 * 语音唤醒的可观察状态。
 *
 * @property enabled 用户是否希望在应用前台监听。
 * @property recognizerAvailable 设备是否提供系统设备端语音识别器。
 * @property status 当前识别或提交阶段。
 * @property triggerPhrases 在最终识别文本开头进行匹配的短语。
 * @property lastCommand 最近一次剥离唤醒短语后提交的命令。
 * @property message 最近一次不可恢复错误的诊断信息。
 */
data class VoiceWakeState(
    val enabled: Boolean = false,
    val recognizerAvailable: Boolean = false,
    val status: VoiceWakeStatus = VoiceWakeStatus.Disabled,
    val triggerPhrases: List<String> = listOf(DEFAULT_WAKE_PHRASE),
    val lastCommand: String? = null,
    val message: String? = null,
) {
    val isListening: Boolean
        get() = status == VoiceWakeStatus.Listening
}

/**
 * 语音唤醒设置页所需的领域状态。
 *
 * @property voiceState 系统识别能力、用户偏好与运行阶段。
 * @property configurationReady 是否存在可提交命令的默认对话模型。
 */
data class VoiceWakeSettings(
    val voiceState: VoiceWakeState,
    val configurationReady: Boolean,
)

/** 将用户输入收敛为可持久化的单行唤醒短语。 */
fun normalizeWakePhrase(phrase: String): String =
    phrase.trim().replace(WHITESPACE_REGEX, " ")

/** 校验供系统识别文本本地匹配的唤醒短语。 */
fun validateWakePhrase(phrase: String): WakePhraseError? {
    val normalized = normalizeWakePhrase(phrase)
    return when {
        normalized.length !in 2..20 -> WakePhraseError.InvalidLength
        normalized.any(Char::isISOControl) -> WakePhraseError.InvalidCharacters
        else -> null
    }
}

const val DEFAULT_WAKE_PHRASE = "吉米"

private val WHITESPACE_REGEX = Regex("\\s+")
