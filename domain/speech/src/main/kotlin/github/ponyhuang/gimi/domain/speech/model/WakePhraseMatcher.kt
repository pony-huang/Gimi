package github.ponyhuang.gimi.domain.speech.model

/**
 * 一次成功的唤醒短语匹配。
 *
 * @property trigger 识别文本中实际命中的原始短语。
 * @property command 剥离唤醒短语后的非空命令文本。
 */
data class WakePhraseMatch(
    val trigger: String,
    val command: String,
)

/** 按 OpenClaw 规则从最终识别文本的起始位置匹配本地唤醒短语。 */
object WakePhraseMatcher {
    private val fillers = setOf("a", "ah", "eh", "er", "erm", "hey", "hmm", "huh", "mhm", "mm", "oh", "uh", "um", "yo", "呃", "嗯", "啊", "诶", "欸")

    fun match(transcript: String, triggers: List<String>): WakePhraseMatch? {
        val normalizedTranscript = normalize(transcript)
        if (normalizedTranscript.value.isEmpty()) return null
        return triggers.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { trigger -> find(transcript, normalizedTranscript, trigger) }
            .minByOrNull { it.first }
            ?.second
    }

    private fun find(
        transcript: String,
        normalized: Normalized,
        trigger: String,
    ): Pair<Int, WakePhraseMatch>? {
        val phrase = normalize(trigger).value
        if (phrase.isEmpty()) return null
        var start = normalized.value.indexOf(phrase)
        while (start >= 0) {
            val end = start + phrase.length
            if (boundary(normalized.value, start - 1, phrase.codePointAt(0)) &&
                boundary(normalized.value, end, phrase.codePointBefore(phrase.length)) &&
                onlyFillers(normalized.value.substring(0, start))
            ) {
                val originalStart = normalized.starts[start]
                val originalEnd = normalized.ends[end - 1]
                val command = transcript.substring(originalEnd).trimStart { !it.isLetterOrDigit() }.trim()
                if (command.isNotEmpty()) {
                    return originalStart to WakePhraseMatch(transcript.substring(originalStart, originalEnd), command)
                }
            }
            start = normalized.value.indexOf(phrase, start + 1)
        }
        return null
    }

    /**
     * 保留到原始识别文本位置映射的规范化文本。
     *
     * @property value 用于不区分大小写匹配的规范化内容。
     * @property starts 每个规范化字符在原文中的起始位置。
     * @property ends 每个规范化字符在原文中的结束位置。
     */
    private data class Normalized(
        val value: String,
        val starts: IntArray,
        val ends: IntArray,
    )

    private fun normalize(source: String): Normalized {
        val value = StringBuilder()
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        var index = 0
        var separatorStart: Int? = null
        var previous: Int? = null
        while (index < source.length) {
            val codePoint = source.codePointAt(index)
            val next = index + Character.charCount(codePoint)
            if (Character.isLetterOrDigit(codePoint)) {
                if (separatorStart != null && previous != null && !noWhitespaceBoundary(previous) && !noWhitespaceBoundary(codePoint)) {
                    value.append(' ')
                    starts += separatorStart
                    ends += index
                }
                String(Character.toChars(codePoint)).lowercase().forEach {
                    value.append(it)
                    starts += index
                    ends += next
                }
                previous = codePoint
                separatorStart = null
            } else if (value.isNotEmpty() && separatorStart == null) {
                separatorStart = index
            }
            index = next
        }
        return Normalized(value.toString(), starts.toIntArray(), ends.toIntArray())
    }

    private fun boundary(value: String, index: Int, edge: Int): Boolean =
        index !in value.indices || !value[index].isLetterOrDigit() || noWhitespaceBoundary(edge)

    private fun noWhitespaceBoundary(codePoint: Int): Boolean = Character.UnicodeScript.of(codePoint) in setOf(
        Character.UnicodeScript.BOPOMOFO, Character.UnicodeScript.HAN, Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.KHMER,
        Character.UnicodeScript.LAO, Character.UnicodeScript.MYANMAR, Character.UnicodeScript.THAI,
    )

    private fun onlyFillers(prefix: String): Boolean = prefix
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter(String::isNotEmpty)
        .all { it.lowercase() in fillers }
}
