package github.ponyhuang.asssistantai.domain.speech.model

enum class SpeechPlaybackStatus { Idle, Loading, Playing, Paused }

data class SpeechPlaybackState(
    val messageId: String? = null,
    val status: SpeechPlaybackStatus = SpeechPlaybackStatus.Idle,
)

data class TtsVoice(
    val id: String,
    val name: String,
    val language: String?,
    val gender: String?,
)

object MiMoTtsVoices {
    val all: List<TtsVoice> = listOf(
        TtsVoice("mimo_default", "MiMo-默认", null, null),
        TtsVoice("冰糖", "冰糖", "中文", "女声"),
        TtsVoice("茉莉", "茉莉", "中文", "女声"),
        TtsVoice("苏打", "苏打", "中文", "男声"),
        TtsVoice("白桦", "白桦", "中文", "男声"),
        TtsVoice("Mia", "Mia", "English", "女声"),
        TtsVoice("Chloe", "Chloe", "English", "女声"),
        TtsVoice("Milo", "Milo", "English", "男声"),
        TtsVoice("Dean", "Dean", "English", "男声"),
    )
}
