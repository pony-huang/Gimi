package github.ponyhuang.gimi.data.modelaccess.minimax.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MiniMax 三个语音端点必须落在同一个基址上。
 *
 * 早期实现把合成、识别、音色列表分别写死在两个域名上，国内账号会因此把合成请求
 * 发到国际站点。三个端点同源后，切换区域只需要改一次服务接入地址。
 */
class MiniMaxEndpointsTest {

    @Test
    fun threeEndpointsShareTheSameBaseUrl() {
        val base = "https://api.minimax.cn/v1"

        assertEquals("https://api.minimax.cn/v1/t2a_v2", MiniMaxEndpoints.tts(base))
        assertEquals("https://api.minimax.cn/v1/speech_to_text", MiniMaxEndpoints.stt(base))
        assertEquals("https://api.minimax.cn/v1/get_voice", MiniMaxEndpoints.voice(base))
    }

    @Test
    fun trailingSlashDoesNotDoubleUp() {
        assertEquals("https://api.minimaxi.com/v1/t2a_v2", MiniMaxEndpoints.tts("https://api.minimaxi.com/v1/"))
    }

    @Test
    fun blankBaseUrlFallsBackToInternationalDefault() {
        assertEquals("https://api.minimaxi.com/v1/t2a_v2", MiniMaxEndpoints.tts(""))
        assertEquals("https://api.minimaxi.com/v1/speech_to_text", MiniMaxEndpoints.stt("   "))
        assertEquals("https://api.minimaxi.com/v1/get_voice", MiniMaxEndpoints.voice("not-a-url"))
    }

    @Test
    fun websocketEndpointReplacesTheVersionSegmentWithItsOwnPath() {
        // WebSocket 的路径不是基址的追加：/v1 要换成 /ws/v1/t2a_v2_bidi。
        assertEquals(
            "wss://api.minimax.cn/ws/v1/t2a_v2_bidi",
            MiniMaxEndpoints.wsBidi("https://api.minimax.cn/v1"),
        )
        assertEquals(
            "wss://api.minimax.cn/ws/v1/t2a_v2_bidi",
            MiniMaxEndpoints.wsBidi("https://api.minimax.cn/v1/"),
        )
        assertEquals(
            "wss://api.minimax.cn/ws/v1/t2a_v2_bidi",
            MiniMaxEndpoints.wsBidi("https://api.minimax.cn"),
        )
    }

    @Test
    fun websocketEndpointKeepsTheSubPathOfSelfHostedProxies() {
        // 自建代理会用子路径部署；只换掉末段 /v1，前面的路径必须保留。
        assertEquals(
            "wss://proxy.example.com/minimax/ws/v1/t2a_v2_bidi",
            MiniMaxEndpoints.wsBidi("https://proxy.example.com/minimax/v1"),
        )
        assertEquals(
            "ws://127.0.0.1:8080/ws/v1/t2a_v2_bidi",
            MiniMaxEndpoints.wsBidi("http://127.0.0.1:8080/v1"),
        )
    }

    @Test
    fun websocketEndpointFallsBackToInternationalDefault() {
        assertEquals(MiniMaxEndpoints.DEFAULT_WS_BIDI, MiniMaxEndpoints.wsBidi(""))
        assertEquals(MiniMaxEndpoints.DEFAULT_WS_BIDI, MiniMaxEndpoints.wsBidi("not-a-url"))
    }
}
