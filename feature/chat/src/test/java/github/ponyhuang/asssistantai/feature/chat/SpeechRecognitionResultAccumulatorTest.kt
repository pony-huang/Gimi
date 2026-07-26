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
}
