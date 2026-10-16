package github.ponyhuang.gimi.domain.sandbox

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** 按 UTF-8 字节保留输出，并附加准确的省略字节数；不会切断字符或输出孤立代理项。 */
fun truncateSandboxOutput(text: String, maxBytes: Int = 10_000): String {
    require(maxBytes >= 0)
    val normalized = buildString {
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            when {
                char.isHighSurrogate() && index < text.length && text[index].isLowSurrogate() -> {
                    append(char); append(text[index++])
                }
                char.isSurrogate() -> append('\uFFFD')
                else -> append(char)
            }
        }
    }
    val bytes = normalized.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return normalized
    val kept = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE)
        .decode(ByteBuffer.wrap(bytes, 0, maxBytes)).toString()
    return "$kept…[truncated ${bytes.size - kept.toByteArray(Charsets.UTF_8).size} bytes]"
}
