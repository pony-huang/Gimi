package github.ponyhuang.gimi.domain.modelcatalog.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 语音端点构建契约：官方默认域名写死，业务空间 ID 非空时改写专属域名，路径保持不变。 */
class SpeechEndpointsTest {

    @Test
    fun `blank workspace id falls back to official default domains`() {
        for (blank in listOf(null, "", "   ")) {
            assertEquals(SpeechEndpoints.QWEN_AUDIO_CN, SpeechEndpoints.qwenInferenceUrl(blank))
            assertEquals(SpeechEndpoints.QWEN_AUDIO_REALTIME_CN, SpeechEndpoints.qwenRealtimeUrl(blank))
        }
    }

    @Test
    fun `workspace id rewrites the host and keeps the protocol path`() {
        assertEquals(
            "wss://ws-123.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime",
            SpeechEndpoints.qwenRealtimeUrl("ws-123"),
        )
        assertEquals(
            "wss://ws-123.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference",
            SpeechEndpoints.qwenInferenceUrl("ws-123"),
        )
    }

    @Test
    fun `workspace id is trimmed before rewriting`() {
        assertEquals(
            "wss://ws-x.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime",
            SpeechEndpoints.qwenRealtimeUrl(" ws-x "),
        )
    }
}
