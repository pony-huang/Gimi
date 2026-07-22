package github.ponyhuang.asssistantai.domain.speech.model

enum class VoiceWakeStatus {
    Stopped,
    Starting,
    WaitingForBluetooth,
    Listening,
    CapturingCommand,
    Transcribing,
    RunningAgent,
    Speaking,
    Paused,
    Error,
}

enum class WakeModelStatus { Missing, Downloading, Extracting, Ready, Error }

data class WakeModelState(
    val status: WakeModelStatus = WakeModelStatus.Missing,
    val progress: Float = 0f,
    val message: String? = null,
)

data class VoiceWakeState(
    val status: VoiceWakeStatus = VoiceWakeStatus.Stopped,
    val keyword: String = DEFAULT_WAKE_KEYWORD,
    val model: WakeModelState = WakeModelState(),
    val deviceName: String? = null,
    val lastCommand: String? = null,
    val message: String? = null,
    val voiceSessionId: String? = null,
) {
    val isRunning: Boolean
        get() = status != VoiceWakeStatus.Stopped && status != VoiceWakeStatus.Error
}

data class VoiceWakeSettings(
    val voiceState: VoiceWakeState,
    val configurationReady: Boolean,
)

const val DEFAULT_WAKE_KEYWORD = "你好助手"

/**
 * 预置唤醒词。
 *
 * [display] 是用户看到并说出的词；[grammar] 是按唤醒模型词表分词、以空格连接的
 * 语法短语（Vosk 语法识别按空格切词，未分词的多字词会因不在词表而被静默丢弃）。
 * 该结构与语言无关：日后支持英文或其他语言时，为对应模型追加预置词即可。
 */
data class WakeKeyword(
    val display: String,
    val grammar: String,
)

val WAKE_KEYWORDS = listOf(
    WakeKeyword(display = "你好助手", grammar = "你好 助手"),
    WakeKeyword(display = "小助手", grammar = "小助手"),
    WakeKeyword(display = "智能助手", grammar = "智能 助手"),
    WakeKeyword(display = "语音助手", grammar = "语音 助手"),
)

fun isPresetWakeKeyword(keyword: String): Boolean =
    WAKE_KEYWORDS.any { it.display == keyword }

/**
 * 将唤醒词转换为语法短语。预置词直接使用其分词结果；非预置词（历史遗留值）
 * 对非拉丁文本按字切分兜底，拉丁文本保持原样（本身以空格分词）。
 */
fun wakeKeywordGrammar(keyword: String): String =
    WAKE_KEYWORDS.firstOrNull { it.display == keyword }?.grammar
        ?: if (keyword.none { it in 'a'..'z' || it in 'A'..'Z' }) {
            keyword.map(Char::toString).joinToString(" ")
        } else {
            keyword
        }
