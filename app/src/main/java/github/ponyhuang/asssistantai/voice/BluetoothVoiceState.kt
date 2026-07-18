package github.ponyhuang.asssistantai.voice

enum class BluetoothVoiceStatus {
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

data class BluetoothVoiceUiState(
    val status: BluetoothVoiceStatus = BluetoothVoiceStatus.Stopped,
    val keyword: String = DEFAULT_WAKE_KEYWORD,
    val model: WakeModelState = WakeModelState(),
    val deviceName: String? = null,
    val lastCommand: String? = null,
    val message: String? = null,
    val voiceSessionId: String? = null,
) {
    val isRunning: Boolean
        get() = status != BluetoothVoiceStatus.Stopped && status != BluetoothVoiceStatus.Error
}

internal fun normalizeWakeText(text: String): String = buildString {
    text.trim().lowercase().forEach { character ->
        if (character.isLetterOrDigit()) append(character)
    }
}

internal fun stripWakeKeyword(transcript: String, keyword: String): String {
    val trimmed = transcript.trim()
    if (trimmed.isEmpty()) return ""
    val normalizedKeyword = normalizeWakeText(keyword)
    if (normalizedKeyword.isEmpty()) return trimmed

    var matchedNormalized = ""
    var cutIndex = 0
    for ((index, character) in trimmed.withIndex()) {
        if (character.isLetterOrDigit()) matchedNormalized += character.lowercase()
        cutIndex = index + 1
        if (matchedNormalized == normalizedKeyword) {
            return trimmed.substring(cutIndex)
                .trimStart { !it.isLetterOrDigit() }
                .trim()
        }
        if (!normalizedKeyword.startsWith(matchedNormalized)) break
    }
    return trimmed
}

const val DEFAULT_WAKE_KEYWORD = "你好助手"
