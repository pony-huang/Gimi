package github.ponyhuang.asssistantai.voice

typealias BluetoothVoiceStatus = github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeStatus
typealias WakeModelStatus = github.ponyhuang.asssistantai.domain.speech.model.WakeModelStatus
typealias WakeModelState = github.ponyhuang.asssistantai.domain.speech.model.WakeModelState
typealias BluetoothVoiceUiState = github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState

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

const val DEFAULT_WAKE_KEYWORD = github.ponyhuang.asssistantai.domain.speech.model.DEFAULT_WAKE_KEYWORD
