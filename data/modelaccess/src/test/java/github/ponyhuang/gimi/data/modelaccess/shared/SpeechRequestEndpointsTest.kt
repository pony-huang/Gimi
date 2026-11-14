package github.ponyhuang.gimi.data.modelaccess.shared

import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import org.junit.Assert.assertEquals
import org.junit.Test

/** 语音请求接入地址解析契约：百炼业务空间改写专属域名，其余沿用连接端点。 */
class SpeechRequestEndpointsTest {

    @Test
    fun `qwen connection rewrites the workspace specific domain`() {
        assertEquals(
            SpeechEndpoints.QWEN_AUDIO_CN,
            speechRequestBaseUrl("qwen", "https://ignored.example.com", workspaceId = null),
        )
        assertEquals(
            "wss://ws-123.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference",
            speechRequestBaseUrl("qwen", "https://ignored.example.com", workspaceId = "ws-123"),
        )
    }

    @Test
    fun `vendor managed connections keep their template endpoint`() {
        // openai 的语音连接在保存时回贴模板官方端点，解析只透传。
        assertEquals(
            "https://api.openai.com/v1",
            speechRequestBaseUrl("openai", "https://api.openai.com/v1", workspaceId = null),
        )
    }

    @Test
    fun `configurable services keep the configured endpoint`() {
        val configured = "https://api.minimaxi.com/v1"
        assertEquals(configured, speechRequestBaseUrl("minimax", configured, workspaceId = null))
        assertEquals(configured, speechRequestBaseUrl("mimo", configured, workspaceId = "ws-123"))
    }
}
