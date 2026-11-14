package github.ponyhuang.gimi.data.modelaccess

/** 将模型正文增量切成适合顺序合成的句子，并限制无标点长回答的等待长度。 */
class VoiceSentenceBuffer {
    private val pending = StringBuilder()

    fun append(delta: String): List<String> {
        pending.append(delta)
        val sentences = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val boundary = pending.indices.firstOrNull { index ->
                pending[index] in "。！？!?；;\n" ||
                    (pending[index] == '.' && index + 1 < pending.length && pending[index + 1].isWhitespace())
            } ?: -1
            var length = when {
                boundary in 0..<MAX_LENGTH -> boundary + 1
                pending.length >= MAX_LENGTH -> MAX_LENGTH
                else -> break
            }
            // 定长截断恰好落在句末标点之前时，把紧随的标点一并带走，
            // 否则下一轮会切出一个只有标点的合成单元（表现为莫名单独停顿）。
            if (boundary == -1) {
                while (length < pending.length && pending[length] in "。！？!?；,.，\n") length++
            }
            if (length < pending.length && pending[length - 1].isHighSurrogate() && pending[length].isLowSurrogate()) length--
            pending.substring(0, length).trim().takeIf { it.isNotEmpty() }?.let(sentences::add)
            pending.delete(0, length)
        }
        return sentences
    }

    fun finish(): String = pending.toString().trim().also { pending.clear() }

    private companion object {
        const val MAX_LENGTH = 100
    }
}
