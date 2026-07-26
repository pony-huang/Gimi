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

object MinimaxTtsVoices {
    val all: List<TtsVoice> = listOf(
        TtsVoice("male-qn-qingse", "青涩男声", "中文", "男声"),
        TtsVoice("audiobook_male_1", "有声书男声 1", "中文", "男声"),
        TtsVoice("Chinese (Mandarin)_Lyrical_Voice", "中文抒情", "中文", "女声"),
        TtsVoice("English_Graceful_Lady", "Graceful Lady (EN)", "English", "女声"),
        TtsVoice("English_Persuasive_Man", "Persuasive Man (EN)", "English", "男声"),
        TtsVoice("English_Lucky_Robot", "Lucky Robot (EN)", "English", "中性"),
        TtsVoice("Japanese_Whisper_Belle", "Whisper Belle (JP)", "日本語", "女声"),
        TtsVoice("Cantonese_GentleLady", "粤语 GentleLady", "粤语", "女声"),
    )
}

/**
 * Maps a configured `LLMModelProvider.serviceId` to its built-in TTS voice catalog.
 * Centralizes the lookup so callers (UI state, future tests) don't hardcode which
 * list to read.
 */
object TtsVoiceCatalog {
    fun forService(serviceId: String?): List<TtsVoice> = when (serviceId) {
        "minimax" -> MinimaxTtsVoices.all
        "mimo" -> MiMoTtsVoices.all
        else -> emptyList()
    }
}
