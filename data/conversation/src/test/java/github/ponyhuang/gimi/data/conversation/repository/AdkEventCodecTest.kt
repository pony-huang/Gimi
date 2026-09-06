package github.ponyhuang.gimi.data.conversation.repository

import com.google.adk.kt.events.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADK 事件携带 `Map<String, Any>`（自定义元数据、工具参数）。普通 kotlinx Json 会因缺少
 * `Any` 序列化器抛 “Serializer for class 'Any' is not found”；[AdkEventCodec] 走 ADK 自带
 * 编解码器，本测试用真实事件证明不会抛该异常，并保留 `Any` 值。
 */
class AdkEventCodecTest {
    @Test
    fun roundTripsEventWithAnyCustomMetadata() {
        val event = Event.Builder()
            .id("event-1")
            .author("assistant")
            .timestamp(1L)
            .turnComplete(true)
            .customMetadata(
                mapOf(
                    "label" to "hello",
                    "tags" to listOf("a", "b"),
                    "count" to 3,
                ),
            )
            .build()

        val json = AdkEventCodec.encode(event)
        val decoded = AdkEventCodec.decode(json)

        assertTrue(json.isNotBlank())
        assertEquals("event-1", decoded.id)
        assertEquals("assistant", decoded.author)
        assertTrue(decoded.turnComplete)
        assertEquals("hello", decoded.customMetadata?.get("label"))
        assertEquals(listOf("a", "b"), decoded.customMetadata?.get("tags"))
    }
}
