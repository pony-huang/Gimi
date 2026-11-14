package github.ponyhuang.gimi.data.modelaccess.mimo.speech

import com.google.gson.Gson
import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MiMo 对话协议音频契约（官方文档 mimo.mi.com/docs/zh-CN/.../audio）：
 * 合成文本进 assistant 消息、audio 只有 format+voice；识别音频是 data-URI 编码、
 * 语种走 asr_options。与 OpenAI 兼容协议同端点不同形，隔离实现后各自锚定官方文档。
 */
class MiMoSpeechProtocolTest {
    private lateinit var server: MockWebServer
    private lateinit var protocol: MiMoSpeechProtocol

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        protocol = MiMoSpeechProtocol(OkHttpClient(), Gson())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `synthesize puts text in assistant message with minimal audio params`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                data: {"choices":[{"delta":{"audio":{"data":"AQID"}}}]}

                data: {"choices":[{"delta":{"audio":{"data":"BAUG"}}}]}

                data: [DONE]
                """.trimIndent(),
            ),
        )

        val audio = protocol.synthesize(synthesisRequest(voiceId = "冰糖")).toList(mutableListOf())

        // SSE 音频分块按序解码
        assertEquals(listOf(byteArrayOf(1, 2, 3).toList(), byteArrayOf(4, 5, 6).toList()), audio.map { it.toList() })
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"mimo-v2.5-tts\""))
        // 目标文本必须在 assistant 消息里（MiMo 契约）
        assertTrue(body.contains("\"role\":\"assistant\""))
        assertTrue(body.contains("你好，MiMo。"))
        // audio 只带 format + voice，官方契约没有 sample_rate
        assertTrue(body.contains("\"audio\":{\"format\":\"pcm16\",\"voice\":\"冰糖\"}"))
        assertFalse(body.contains("sample_rate"))
    }

    @Test
    fun `synthesize falls back to mimo_default when voice is blank`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"AQID\"}}}]}\n\ndata: [DONE]\n"),
        )

        protocol.synthesize(synthesisRequest(voiceId = "")).toList(mutableListOf())

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"voice\":\"mimo_default\""))
    }

    @Test
    fun `synthesize surfaces http failure with detail`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid key"))

        val failure = runCatching {
            runBlocking { protocol.synthesize(synthesisRequest()).toList(mutableListOf()) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("HTTP 401"))
        assertTrue(failure?.message.orEmpty().contains("invalid key"))
    }

    @Test
    fun `recognize uploads data-uri wav and asr_options language`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"  你好，MiMo。  "}}]}"""))

        val result = protocol.recognize(recognitionRequest(language = "zh"))

        assertEquals("  你好，MiMo。  ", result)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        // MiMo 契约：音频数据是带 data: 前缀的 data-URI，且没有独立 format 字段
        assertTrue(body, body.contains("data:audio/wav;base64,"))
        assertFalse(body, body.contains("\"format\""))
        // RIFF 头在 JSON 里是 base64 形态；"RIF" 三字节的编码前缀 UklGR 稳定不变
        assertTrue(body, body.contains("UklGR"))
        assertTrue(body, body.contains("\"asr_options\":{\"language\":\"zh\"}"))
    }

    @Test
    fun `recognize defaults language to auto when blank`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"hello"}}]}"""))

        protocol.recognize(recognitionRequest(language = ""))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"asr_options\":{\"language\":\"auto\"}"))
    }

    @Test
    fun `recognize surfaces http failure`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

        val failure = runCatching {
            runBlocking { protocol.recognize(recognitionRequest()) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("HTTP 403"))
    }

    private fun synthesisRequest(voiceId: String = "mimo_default") = SynthesisRequest(
        text = "你好，MiMo。",
        voiceId = voiceId,
        modelId = "mimo-v2.5-tts",
        apiKey = "secret",
        baseUrl = server.url("/v1").toString(),
    )

    private fun recognitionRequest(language: String = "auto") = RecognitionRequest(
        pcm16 = byteArrayOf(1, 2, 3, 4),
        modelId = "mimo-v2.5-asr",
        apiKey = "secret",
        baseUrl = server.url("/v1").toString(),
        language = language,
    )
}
