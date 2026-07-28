package github.ponyhuang.asssistantai.feature.chat

/**
 * Accumulates partial and final speech recognition results into a single text.
 *
 * Pure logic with no Android dependencies so it can be unit tested on the JVM.
 */
internal class SpeechRecognitionResultAccumulator {
    private val committed = mutableListOf<String>()

    fun preview(value: String, commit: Boolean): String {
        val normalized = value.trim()
        if (commit && normalized.isNotEmpty() && committed.lastOrNull() != normalized) {
            committed += normalized
        }
        return (committed + normalized.takeIf { !commit && it.isNotEmpty() }.orEmpty())
            .joinToString(separator = "")
    }

    fun complete(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isNotEmpty() && committed.lastOrNull() != normalized) {
            committed += normalized
        }
        return committed.joinToString(separator = "")
    }

    fun reset() {
        committed.clear()
    }
}
