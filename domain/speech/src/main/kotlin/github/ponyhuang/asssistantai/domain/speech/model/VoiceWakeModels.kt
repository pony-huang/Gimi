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
