package github.ponyhuang.gimi.data.agent.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 语音服务目录保存的是 wss 接入端点；工具元数据里的 fullBaseUrl 必须是可按 URI
 * 解析的 http(s) 端点，不能把 wss 原样写进 ToolRunMetadata。
 */
class RealtimeModelBaseUrlTest {

    @Test
    fun `wss endpoint is normalized to https and trailing slash removed`() {
        assertEquals(
            "https://dashscope.aliyuncs.com/api-ws/v1/realtime",
            realtimeModelBaseUrl("  wss://dashscope.aliyuncs.com/api-ws/v1/realtime/  "),
        )
    }

    @Test
    fun `ws endpoint is normalized to http`() {
        assertEquals(
            "http://example.test/realtime",
            realtimeModelBaseUrl("ws://example.test/realtime"),
        )
    }

    @Test
    fun `scheme comparison ignores case`() {
        assertEquals(
            "https://example.test/ws",
            realtimeModelBaseUrl("WSS://example.test/ws/"),
        )
    }

    @Test
    fun `plain http endpoint is kept with trailing slash trimmed`() {
        assertEquals(
            "https://api.openai.com/v1",
            realtimeModelBaseUrl("https://api.openai.com/v1///"),
        )
    }
}
