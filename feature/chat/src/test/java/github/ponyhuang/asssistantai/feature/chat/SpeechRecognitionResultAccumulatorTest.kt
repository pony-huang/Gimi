package github.ponyhuang.asssistantai.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRecognitionResultAccumulatorTest {

    @Test
    fun `final callback includes text supplied only by onResults`() {
        val accumulator = SpeechRecognitionResultAccumulator()

        assertEquals("最终文本", accumulator.complete("最终文本"))
    }

    @Test
    fun `final callback does not duplicate the last committed partial result`() {
        val accumulator = SpeechRecognitionResultAccumulator()

        accumulator.preview("你好", commit = true)

        assertEquals("你好", accumulator.complete("你好"))
    }

    @Test
    fun `reset discards results from the previous recognition`() {
        val accumulator = SpeechRecognitionResultAccumulator()
        accumulator.preview("上一轮", commit = true)

        accumulator.reset()

        assertEquals("下一轮", accumulator.complete("下一轮"))
    }

    @Test
    fun `committing partial results are concatenated without separator`() {
        val accumulator = SpeechRecognitionResultAccumulator()

        assertEquals("你好", accumulator.preview("你好", commit = true))
        assertEquals("你好世界", accumulator.preview("世界", commit = true))
    }

    @Test
    fun `consecutive identical committed partials are only kept once`() {
        val accumulator = SpeechRecognitionResultAccumulator()

        accumulator.preview("你好", commit = true)

        assertEquals("你好", accumulator.preview("你好", commit = true))
    }

    @Test
    fun `non-committing partial preview is not accumulated`() {
        val accumulator = SpeechRecognitionResultAccumulator()

        assertEquals("你", accumulator.preview("你", commit = false))
        assertEquals("你好", accumulator.preview("你好", commit = false))
    }

    @Test
    fun `non-committing partial preview is appended after committed segments`() {
        val accumulator = SpeechRecognitionResultAccumulator()
        accumulator.preview("你好", commit = true)

        assertEquals("你好世", accumulator.preview("世", commit = false))
    }

    @Test
    fun `blank committing partial is ignored`() {
        val accumulator = SpeechRecognitionResultAccumulator()

        assertEquals("", accumulator.preview("   ", commit = true))
        assertEquals("文本", accumulator.complete("文本"))
    }

    @Test
    fun `blank non-committing partial yields only committed segments`() {
        val accumulator = SpeechRecognitionResultAccumulator()
        accumulator.preview("你好", commit = true)

        assertEquals("你好", accumulator.preview("", commit = false))
    }

    @Test
    fun `partial values are trimmed before accumulation`() {
        val accumulator = SpeechRecognitionResultAccumulator()

        assertEquals("你好", accumulator.preview("  你好  ", commit = true))
    }

    @Test
    fun `complete with null returns only committed segments`() {
        val accumulator = SpeechRecognitionResultAccumulator()
        accumulator.preview("你好", commit = true)

        assertEquals("你好", accumulator.complete(null))
    }

    @Test
    fun `complete with null on empty accumulator returns empty text`() {
        val accumulator = SpeechRecognitionResultAccumulator()

        assertEquals("", accumulator.complete(null))
    }

    @Test
    fun `complete with blank text returns only committed segments`() {
        val accumulator = SpeechRecognitionResultAccumulator()
        accumulator.preview("你好", commit = true)

        assertEquals("你好", accumulator.complete("   "))
    }
}
