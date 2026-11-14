package github.ponyhuang.gimi.data.modelaccess.deepseek.speech

import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
import github.ponyhuang.gimi.domain.voice.repository.VoiceAuthenticationException
import java.io.IOException
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

/** DeepSeek 独立语音识别端点的 multipart 上传契约。 */
class DeepSeekSpeechRecognizerTest {
    private lateinit var server: MockWebServer
    private lateinit var recognizer: DeepSeekSpeechRecognizer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        recognizer = DeepSeekSpeechRecognizer(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun recognize_uploadsWavOnFixedToolsEndpoint() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"上海今天天气怎么样？"}"""))

        val result = recognizer.recognize(request())

        assertEquals("上海今天天气怎么样？", result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/tools/asr", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val body = recorded.body.readUtf8()
        // 请求只有 file 一个表单字段：端点不带模型与语种参数
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"recording.wav\""))
        assertTrue(body.contains("RIFF"))
        assertFalse(body.contains("name=\"model\""))
        assertFalse(body.contains("name=\"language\""))
    }

    @Test
    fun recognize_surfacesHttpFailureWithDetail() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("invalid audio format"))

        val failure = runCatching {
            runBlocking { recognizer.recognize(request()) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("HTTP 400"))
        assertTrue(failure?.message.orEmpty().contains("invalid audio format"))
    }

    @Test
    fun recognize_mapsAuthenticationFailureToTypedError() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid key"))

        val failure = runCatching {
            runBlocking { recognizer.recognize(request()) }
        }.exceptionOrNull()

        assertTrue(failure is VoiceAuthenticationException)
    }

    @Test
    fun recognize_rejectsBlankText() {
        server.enqueue(MockResponse().setBody("""{"text":"  "}"""))

        val failure = runCatching {
            runBlocking { recognizer.recognize(request()) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("未返回文本"))
    }

    private fun request() = RecognitionRequest(
        pcm16 = byteArrayOf(1, 2, 3, 4),
        modelId = "deepseek-asr",
        apiKey = "secret",
        baseUrl = server.url("/").toString(),
    )
}
