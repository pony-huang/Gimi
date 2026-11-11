package github.ponyhuang.gimi.data.agent.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `response.done` 状态处置：只有明确未正常完成的状态丢弃未定稿调用，未知终态
 * 必须按完成兜底（否则厂商新增状态字符串会静默漏掉合法工具调用）。
 */
class RealtimeResponseDispositionTest {

    @Test
    fun `missing or blank status flushes`() {
        assertEquals(RealtimeResponseDisposition.FLUSH, realtimeResponseDisposition(null))
        assertEquals(RealtimeResponseDisposition.FLUSH, realtimeResponseDisposition(""))
        assertEquals(RealtimeResponseDisposition.FLUSH, realtimeResponseDisposition("  "))
    }

    @Test
    fun `completed statuses flush case insensitively`() {
        listOf("completed", "COMPLETED", " done ", "finished", "succeeded", "success")
            .forEach { status ->
                assertEquals(status, RealtimeResponseDisposition.FLUSH, realtimeResponseDisposition(status))
            }
    }

    @Test
    fun `explicit cancelled incomplete and failed statuses discard`() {
        listOf("cancelled", "canceled", "CANCELLED", " incomplete ", "failed", "FAILED")
            .forEach { status ->
                assertEquals(status, RealtimeResponseDisposition.DISCARD, realtimeResponseDisposition(status))
            }
    }

    @Test
    fun `unknown vendor status is reported instead of being silently dropped`() {
        assertEquals(
            RealtimeResponseDisposition.UNKNOWN,
            realtimeResponseDisposition("stopped_by_new_policy"),
        )
    }
}
