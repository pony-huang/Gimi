package github.ponyhuang.gimi.data.modelaccess.deepseek.speech

import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
import github.ponyhuang.gimi.domain.voice.repository.VoiceAuthenticationException
import github.ponyhuang.gimi.domain.voice.repository.VoiceServiceException
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** DeepSeek 语音合成端点的请求与二进制流契约。 */
class DeepSeekSpeechSynthesizerTest {
    private lateinit var server: MockWebServer
    private lateinit var synthesizer: DeepSeekSpeechSynthesizer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        synthesizer = DeepSeekSpeechSynthesizer(OkHttpClient())
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
                .setHeader("Content-Type", "audio/wav")
                .setBody(Buffer().write(audio)),
        )

        val chunks = synthesizer.synthesize(request()).toList()

        // 请求体契约：pcm 裸流格式，voice_id 跟随所选音色
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/tools/tts", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("你好", body.getValue("text").jsonPrimitive.content)
        assertEquals("stella", body.getValue("voice_id").jsonPrimitive.content)
        assertEquals("pcm", body.getValue("format").jsonPrimitive.content)
        // 响应按原始二进制分块产出，拼接后与完整音频一致
        assertTrue(chunks.size > 1)
        assertArrayEquals(audio, chunks.reduce { acc, chunk -> acc + chunk })
    }

    @Test
    fun synthesize_fallsBackToDefaultVoiceWhenBlank() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3))))

        synthesizer.synthesize(request(voiceId = "")).toList()

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("stella", body.getValue("voice_id").jsonPrimitive.content)
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
    fun synthesize_mapsAuthenticationFailureToTypedError() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid key"))

        val failure = runCatching {
            runBlocking { synthesizer.synthesize(request()).toList() }
        }.exceptionOrNull()

        assertTrue(failure is VoiceAuthenticationException)
    }

    @Test
    fun synthesize_surfacesServiceFailureAsTypedError() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Content Exists Risk"))

        val failure = runCatching {
            runBlocking { synthesizer.synthesize(request()).toList() }
        }.exceptionOrNull()

        assertTrue(failure is VoiceServiceException)
        assertTrue(failure?.message.orEmpty().contains("HTTP 400"))
    }

    private fun request(voiceId: String = "stella") = SynthesisRequest(
        text = "你好",
        voiceId = voiceId,
        modelId = "deepseek-tts",
        apiKey = "secret",
        baseUrl = server.url("/").toString(),
    )
}
