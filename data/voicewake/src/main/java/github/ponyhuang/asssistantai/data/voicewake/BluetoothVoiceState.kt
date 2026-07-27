package github.ponyhuang.asssistantai.data.voicewake

typealias BluetoothVoiceStatus = github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeStatus
typealias WakeModelStatus = github.ponyhuang.asssistantai.domain.speech.model.WakeModelStatus
typealias WakeModelState = github.ponyhuang.asssistantai.domain.speech.model.WakeModelState
typealias BluetoothVoiceUiState = github.ponyhuang.asssistantai.domain.speech.model.VoiceWakeState

fun normalizeWakeText(text: String): String = buildString {
    text.trim().lowercase().forEach { character ->
        if (character.isLetterOrDigit()) append(character)
    }
}

fun stripWakeKeyword(transcript: String, keyword: String): String {
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

val DEFAULT_WAKE_KEYWORD = github.ponyhuang.asssistantai.domain.speech.model.DEFAULT_WAKE_KEYWORD

fun voiceConfirmationTarget(arguments: Map<String, Any?>): String =
    arguments.entries
        .joinToString("，") { (key, rawValue) ->
            val value = rawValue?.toString().orEmpty()
            val spokenValue = if (
                key.contains("phone", ignoreCase = true) ||
                key.contains("number", ignoreCase = true)
            ) {
                value.takeLast(4)
            } else {
                value.take(40)
            }
            "$key $spokenValue"
        }
        .take(120)

fun isVoiceConfirmationApproved(
    transcript: String,
    confirmWords: List<String>,
    rejectWords: List<String>,
): Boolean {
    val normalized = transcript.trim().lowercase()
    val rejected = rejectWords.any(normalized::contains)
    if (rejected) return false
    return confirmWords.any(normalized::contains)
}
