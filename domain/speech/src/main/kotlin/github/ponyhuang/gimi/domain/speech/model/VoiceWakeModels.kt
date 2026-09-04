package github.ponyhuang.gimi.domain.speech.model

enum class VoiceWakeStatus {
    Stopped,
    Starting,
    Listening,
    CapturingCommand,
    Transcribing,
    RunningAgent,
    Speaking,
    Paused,
    Error,
}

enum class WakeModelStatus { Missing, Downloading, Extracting, Ready, Removing, Error }

/** 自定义唤醒词未通过当前语言模型规则的原因。 */
enum class WakeKeywordError { InvalidLength, InvalidCharacters, InvalidWordFormat }

/** 携带类型化唤醒词错误，供展示层映射本地化文案。 */
class WakeKeywordException(val error: WakeKeywordError) : IllegalArgumentException(error.name)

/** 单个离线唤醒模型的安装状态、进度与失败信息。 */
data class WakeModelState(
    val status: WakeModelStatus = WakeModelStatus.Missing,
    val progress: Float = 0f,
    val message: String? = null,
)

/**
 * 一个可用的唤醒模型。纯 Kotlin 描述，不含任何 Android/资源依赖；
 * 展示名由 UI 层按模型 id 映射到字符串资源。
 *
 * @property defaultWakeWord 该模型默认展示给用户的唤醒词。
 * @property defaultWakeWordGrammar 默认唤醒词在离线识别器中的词元语法。
 * @property recommendedWakeWords 用户可快速填入的模型相关唤醒词。
 */
data class WakeModelInfo(
    val id: String,
    val languageTag: String,
    val source: WakeModelSource,
    val sha256: String,
    val defaultWakeWord: String,
    val defaultWakeWordGrammar: String,
    val recommendedWakeWords: List<String>,
    /** 语音确认词表 —— 跟随模型语言（用户对设备说的是模型语言，而非界面语言）。 */
    val confirmWords: List<String>,
    val rejectWords: List<String>,
    /** TTS 确认提示模板，%1$s 为工具名，使用模型语言。 */
    val confirmationPromptTemplate: String,
)

sealed interface WakeModelSource {
    /** 随 APK 资源提供的模型压缩包。 */
    data class Bundled(val assetPath: String) : WakeModelSource

    /** 需要按 URL 下载的模型压缩包及其预估大小。 */
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
        defaultWakeWord = "吉米",
        defaultWakeWordGrammar = "吉米",
        recommendedWakeWords = listOf("吉米", "小助手", "语音助手"),
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
        defaultWakeWord = "Gimi",
        // 英语模型词表不包含品牌拼写 gimi，但包含同音词 jimmy。
        defaultWakeWordGrammar = "jimmy",
        recommendedWakeWords = listOf("Gimi", "hey assistant", "voice assistant"),
        confirmWords = listOf("confirm", "yes", "proceed"),
        rejectWords = listOf("cancel", "no", "don't"),
        confirmationPromptTemplate = "Tool %1${'$'}s needs your confirmation. " +
            "Say confirm, yes, or proceed to allow; say cancel or no to decline.",
    )

    val models: List<WakeModelInfo> = listOf(Chinese, English)

    val default: WakeModelInfo = Chinese

    fun byId(id: String): WakeModelInfo? = models.firstOrNull { it.id == id }
}

/** 语音唤醒服务的可观察运行状态。 */
data class VoiceWakeState(
    val status: VoiceWakeStatus = VoiceWakeStatus.Stopped,
    val availableModels: List<WakeModelInfo> = listOf(WakeModelCatalog.default),
    val activeModelId: String = WakeModelCatalog.default.id,
    /** 当前模型已保存并实际生效的唤醒词。 */
    val wakeWord: String = WakeModelCatalog.default.defaultWakeWord,
    val modelStates: Map<String, WakeModelState> = emptyMap(),
    val deviceName: String? = null,
    val lastCommand: String? = null,
    val message: String? = null,
) {
    /** 激活模型的安装状态（派生自 modelStates）。 */
    val model: WakeModelState
        get() = modelStates[activeModelId] ?: WakeModelState()

    val activeModel: WakeModelInfo
        get() = availableModels.firstOrNull { it.id == activeModelId } ?: WakeModelCatalog.default

    val isRunning: Boolean
        get() = status != VoiceWakeStatus.Stopped && status != VoiceWakeStatus.Error
}

/** 将用户输入收敛为可持久化的单行唤醒词。 */
fun normalizeWakeKeyword(keyword: String): String =
    keyword.trim().replace(WHITESPACE_REGEX, " ")

/** 按唤醒模型语言校验用户输入。 */
fun validateWakeKeyword(keyword: String, model: WakeModelInfo): WakeKeywordError? {
    val value = normalizeWakeKeyword(keyword)
    return if (model.languageTag.startsWith("en")) {
        when {
            value.length !in 2..40 -> WakeKeywordError.InvalidLength
            value.split(' ').size !in 1..4 ||
                value.split(' ').any { !it.matches(ENGLISH_WORD_REGEX) } ->
                WakeKeywordError.InvalidWordFormat
            else -> null
        }
    } else {
        when {
            value.length !in 2..20 -> WakeKeywordError.InvalidLength
            value.any { it.isISOControl() } -> WakeKeywordError.InvalidCharacters
            else -> null
        }
    }
}

/** 将展示唤醒词转换为 Vosk 限定语法使用的识别词元。 */
fun wakeWordGrammar(keyword: String, model: WakeModelInfo): String {
    val normalized = normalizeWakeKeyword(keyword)
    if (normalized.equals(model.defaultWakeWord, ignoreCase = true)) {
        return model.defaultWakeWordGrammar
    }
    return if (model.languageTag.startsWith("en")) {
        normalized.lowercase()
    } else {
        CHINESE_WAKE_WORD_GRAMMARS[normalized] ?: normalized
    }
}

private val WHITESPACE_REGEX = Regex("\\s+")
private val ENGLISH_WORD_REGEX = Regex("[A-Za-z']+")
private val CHINESE_WAKE_WORD_GRAMMARS = mapOf(
    "小助手" to "小助手",
    "语音助手" to "语音 助手",
)

/** 语音唤醒设置页所需的领域状态。 */
data class VoiceWakeSettings(
    val voiceState: VoiceWakeState,
    val configurationReady: Boolean,
)
