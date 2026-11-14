package github.ponyhuang.gimi.data.modelaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 流式边界不能重复句子、误拆小数或拆坏 UTF-16 字符。 */
class VoiceSentenceBufferTest {
    @Test
    fun preservesPartialSentenceAcrossDeltas() {
        val buffer = VoiceSentenceBuffer()
        assertTrue(buffer.append("你好").isEmpty())
        assertEquals(listOf("你好。", "继续说！"), buffer.append("。继续说！最后半句"))
        assertEquals("最后半句", buffer.finish())
        assertEquals("", buffer.finish())
    }

    @Test
    fun englishSentenceBoundaryDoesNotSplitDecimal() {
        val buffer = VoiceSentenceBuffer()
        assertTrue(buffer.append("The value is 3.").isEmpty())
        assertEquals(listOf("The value is 3.14."), buffer.append("14. Next sentence"))
        assertEquals("Next sentence", buffer.finish())
    }

    @Test
    fun lengthBoundDoesNotSplitSurrogatePair() {
        val buffer = VoiceSentenceBuffer()
        val prefix = "a".repeat(99)
        val chunks = buffer.append(prefix + "😀tail")
        assertEquals(listOf(prefix), chunks)
        assertEquals("😀tail", buffer.finish())
    }
}
