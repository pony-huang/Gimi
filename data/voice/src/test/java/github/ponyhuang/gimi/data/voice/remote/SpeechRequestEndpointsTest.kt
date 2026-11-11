package github.ponyhuang.gimi.data.voice.remote

import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import org.junit.Assert.assertEquals
import org.junit.Test

/** 语音请求接入地址解析契约：厂商自有端点写死官方地址，可配置端点沿用用户基址。 */
class SpeechRequestEndpointsTest {

    @Test
    fun `qwen service resolves vendor owned endpoint with optional workspace`() {
        assertEquals(
            SpeechEndpoints.QWEN_AUDIO_CN,
            speechRequestBaseUrl("qwen-audio", "https://ignored.example.com", workspaceId = null),
        )
        assertEquals(
            "wss://ws-123.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference",
            speechRequestBaseUrl("qwen-audio", "https://ignored.example.com", workspaceId = "ws-123"),
        )
    }

    @Test
    fun `openai speech service resolves to the official base only`() {
        assertEquals(
            SpeechEndpoints.OPENAI_SPEECH_BASE_URL,
            speechRequestBaseUrl("openai-speech", "https://proxy.example.com/v1", workspaceId = null),
        )
    }

    @Test
    fun `configurable services keep the user configured base url`() {
        val configured = "https://api.minimaxi.com/v1"
        assertEquals(configured, speechRequestBaseUrl("minimax-speech", configured, workspaceId = null))
        assertEquals(configured, speechRequestBaseUrl("mimo-speech", configured, workspaceId = "ws-123"))
    }
}
