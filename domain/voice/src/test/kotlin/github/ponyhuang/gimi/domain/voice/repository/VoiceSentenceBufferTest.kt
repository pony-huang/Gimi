package github.ponyhuang.gimi.domain.voice.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 流式边界不能重复句子、误拆小数或拆坏 UTF-16 字符；纯 emoji/标点单元不产生合成请求。 */
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
    fun dropsTrailingUnitsWithoutAnyLetterOrDigit() {
        // 真机故障形态：回复以仅含 emoji 的尾块结尾，百炼对其返回 InvalidParameter
        // "Please ensure input text is valid"，整轮在音频播完后被标失败。
        val emoji = VoiceSentenceBuffer()
        assertEquals(listOf("好的！"), emoji.append("好的！😊"))
        assertEquals("", emoji.finish())

        val ellipsis = VoiceSentenceBuffer()
        assertEquals(listOf("先说这些。"), ellipsis.append("先说这些。……"))
        assertEquals("", ellipsis.finish())
    }

    @Test
    fun keepsUnitsThatMixUnpronounceableMarksWithSpeakableText() {
        val buffer = VoiceSentenceBuffer()
        assertEquals(listOf("3.14。"), buffer.append("3.14。😊你好"))
        assertEquals("😊你好", buffer.finish())
        val ellipsis = VoiceSentenceBuffer()
        assertTrue(ellipsis.append("这个嘛……就这样").isEmpty())
        assertEquals("这个嘛……就这样", ellipsis.finish())
    }
}
