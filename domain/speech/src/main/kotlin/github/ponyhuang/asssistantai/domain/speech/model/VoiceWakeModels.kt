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

/**
 * 唤醒词校验结果。错误以枚举形式传递给 UI 层，由 UI 按 locale 映射文案，
 * 避免底层硬编码自然语言。
 */
enum class WakeKeywordError { InvalidLength, InvalidCharacters, InvalidWordFormat }

class WakeKeywordException(val error: WakeKeywordError) : IllegalArgumentException()

/**
 * 一个可用的唤醒模型。纯 Kotlin 描述，不含任何 Android/资源依赖；
 * 展示名由 UI 层按模型 id 映射到字符串资源。
 */
data class WakeModelInfo(
    val id: String,
    val languageTag: String,
    val source: WakeModelSource,
    val sha256: String,
    val defaultKeyword: String,
    /** 语音确认词表 —— 跟随模型语言（用户对设备说的是模型语言，而非界面语言）。 */
    val confirmWords: List<String>,
    val rejectWords: List<String>,
    /** TTS 确认提示模板，%1$s 为工具名，使用模型语言。 */
    val confirmationPromptTemplate: String,
)

sealed interface WakeModelSource {
    data class Bundled(val assetPath: String) : WakeModelSource
    data class Downloadable(val url: String, val sizeBytes: Long) : WakeModelSource
}

object WakeModelCatalog {
    val Chinese = WakeModelInfo(
        id = "vosk-small-cn",
        languageTag = "zh-CN",
        source = WakeModelSource.Downloadable(
            url = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
            sizeBytes = 42_000_000L,
        ),
        sha256 = "3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba",
        defaultKeyword = "你好助手",
        confirmWords = listOf("确认", "允许", "执行"),
        rejectWords = listOf("取消", "拒绝", "不要"),
        confirmationPromptTemplate = "需要执行工具 %1${'$'}s。请说确认、允许或执行；如需取消，请说取消、拒绝或不要。",
    )

    val English = WakeModelInfo(
        id = "vosk-small-en-us",
        languageTag = "en-US",
        source = WakeModelSource.Downloadable(
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            sizeBytes = 41_205_931L,
        ),
        // vosk-model-small-en-us-0.15（Apache-2.0），哈希于 2026-07 本地校验。
        sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
        defaultKeyword = "hey assistant",
        confirmWords = listOf("confirm", "yes", "proceed"),
        rejectWords = listOf("cancel", "no", "don't"),
        confirmationPromptTemplate = "Tool %1${'$'}s needs your confirmation. " +
            "Say confirm, yes, or proceed to allow; say cancel or no to decline.",
    )

    val models: List<WakeModelInfo> = listOf(Chinese, English)

    val default: WakeModelInfo = Chinese

    fun byId(id: String): WakeModelInfo? = models.firstOrNull { it.id == id }
}

/**
 * 校验唤醒词是否满足指定语言模型的要求。
 * - 中文：2–20 个字符，不允许控制字符。
 * - 英语：2–4 个小写单词、单空格分隔、每词仅 [a-z']（Vosk 语法识别要求词表内分词）。
 */
fun validateWakeKeyword(keyword: String, languageTag: String): WakeKeywordError? {
    val value = keyword.trim()
    return if (languageTag.startsWith("en")) {
        if (value.length !in 2..40) return WakeKeywordError.InvalidLength
        val words = value.split(' ')
        if (words.size !in 2..4 || words.any { !it.matches(ENGLISH_WORD_REGEX) }) {
            WakeKeywordError.InvalidWordFormat
        } else {
            null
        }
    } else {
        when {
            value.length !in 2..20 -> WakeKeywordError.InvalidLength
            value.any { it.isISOControl() } -> WakeKeywordError.InvalidCharacters
            else -> null
        }
    }
}

private val ENGLISH_WORD_REGEX = Regex("[a-z']+")

/**
 * Known Chinese wake words whose Vosk grammar needs explicit word boundaries.
 * Other non-Latin values fall back to per-character grammar for compatibility
 * with previously persisted custom wake words.
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

fun wakeKeywordGrammar(keyword: String): String =
    WAKE_KEYWORDS.firstOrNull { it.display == keyword }?.grammar
        ?: if (keyword.none { it in 'a'..'z' || it in 'A'..'Z' }) {
            keyword.map(Char::toString).joinToString(" ")
        } else {
            keyword
        }

data class VoiceWakeState(
    val status: VoiceWakeStatus = VoiceWakeStatus.Stopped,
    val keyword: String = DEFAULT_WAKE_KEYWORD,
    val availableModels: List<WakeModelInfo> = listOf(WakeModelCatalog.default),
    val activeModelId: String = WakeModelCatalog.default.id,
    val modelStates: Map<String, WakeModelState> = emptyMap(),
    val deviceName: String? = null,
    val lastCommand: String? = null,
    val message: String? = null,
    val voiceSessionId: String? = null,
) {
    /** 激活模型的安装状态（派生自 modelStates）。 */
    val model: WakeModelState
        get() = modelStates[activeModelId] ?: WakeModelState()

    val activeModel: WakeModelInfo
        get() = availableModels.firstOrNull { it.id == activeModelId } ?: WakeModelCatalog.default

    val isRunning: Boolean
        get() = status != VoiceWakeStatus.Stopped && status != VoiceWakeStatus.Error
}

data class VoiceWakeSettings(
    val voiceState: VoiceWakeState,
    val configurationReady: Boolean,
)

val DEFAULT_WAKE_KEYWORD: String = WakeModelCatalog.Chinese.defaultKeyword
