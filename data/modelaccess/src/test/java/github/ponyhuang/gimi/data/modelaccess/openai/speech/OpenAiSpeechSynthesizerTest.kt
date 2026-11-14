package github.ponyhuang.gimi.data.modelaccess.openai.speech

import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** OpenAI 语音合成端点的请求与二进制流契约。 */
class OpenAiSpeechSynthesizerTest {
    private lateinit var server: MockWebServer
    private lateinit var synthesizer: OpenAiSpeechSynthesizer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        synthesizer = OpenAiSpeechSynthesizer(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun synthesize_postsPcmContractAndStreamsBinaryInOrder() = runTest {
        val audio = ByteArray(10_000) { (it % 251).toByte() }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(Buffer().write(audio)),
        )

        val chunks = synthesizer.synthesize(request()).toList()

        // 请求体契约：pcm 裸流格式，voice 跟随所选音色
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/audio/speech", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        val body = com.google.gson.JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals("gpt-4o-mini-tts", body.get("model").asString)
        assertEquals("你好", body.get("input").asString)
        assertEquals("ash", body.get("voice").asString)
        assertEquals("pcm", body.get("response_format").asString)
        // 响应按原始二进制分块产出，拼接后与完整音频一致
        assertTrue(chunks.size > 1)
        assertArrayEquals(audio, chunks.reduce { acc, chunk -> acc + chunk })
    }

    @Test
    fun synthesize_fallsBackToDefaultVoiceWhenBlank() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3))))

        synthesizer.synthesize(request(voiceId = "")).toList()

        val body = com.google.gson.JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("alloy", body.get("voice").asString)
    }

    @Test
    fun synthesize_rejectsEmptyAudio() {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(0))))

        val failure = runCatching {
            runBlocking { synthesizer.synthesize(request()).toList() }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("未返回音频数据"))
    }

    @Test
    fun synthesize_surfacesHttpFailure() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid key"))

        val failure = runCatching {
            runBlocking { synthesizer.synthesize(request()).toList() }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("HTTP 401"))
        assertTrue(failure?.message.orEmpty().contains("invalid key"))
    }

    private fun request(voiceId: String = "ash") = SynthesisRequest(
        text = "你好",
        voiceId = voiceId,
        modelId = "gpt-4o-mini-tts",
        apiKey = "secret",
        baseUrl = server.url("/v1").toString(),
    )
}
