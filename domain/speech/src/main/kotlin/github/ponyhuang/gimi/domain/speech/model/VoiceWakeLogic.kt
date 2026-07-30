package github.ponyhuang.gimi.domain.speech.model

import java.util.Locale

fun normalizeWakeText(text: String): String = buildString {
    text.trim().lowercase(Locale.ROOT).forEach { character ->
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

private const val PHONE_SUFFIX_LENGTH = 4
private const val ARGUMENT_VALUE_MAX_LENGTH = 40
private const val CONFIRMATION_SPEECH_MAX_LENGTH = 120

fun voiceConfirmationTarget(arguments: Map<String, Any?>): String =
    arguments.entries
        .joinToString("，") { (key, rawValue) ->
            val value = rawValue?.toString().orEmpty()
            val spokenValue = if (
                key.contains("phone", ignoreCase = true) ||
                key.contains("number", ignoreCase = true)
            ) {
                value.takeLast(PHONE_SUFFIX_LENGTH)
            } else {
                value.take(ARGUMENT_VALUE_MAX_LENGTH)
            }
            "$key $spokenValue"
        }
        .take(CONFIRMATION_SPEECH_MAX_LENGTH)

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
