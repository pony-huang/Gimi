package github.ponyhuang.gimi.data.modelaccess.openai.speech

import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
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

/** OpenAI 专用转写端点的 multipart 上传契约。 */
class OpenAiSpeechRecognizerTest {
    private lateinit var server: MockWebServer
    private lateinit var recognizer: OpenAiSpeechRecognizer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        recognizer = OpenAiSpeechRecognizer(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun recognize_uploadsWavUsingOpenAiMultipartContract() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"  你好，OpenAI。  "}"""))

        val result = recognizer.recognize(request(language = "zh"))

        assertEquals("  你好，OpenAI。  ", result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/audio/transcriptions", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("gpt-4o-transcribe"))
        assertTrue(body.contains("name=\"response_format\""))
        assertTrue(body.contains("json"))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("zh"))
        assertTrue(body.contains("filename=\"recording.wav\""))
        assertTrue(body.contains("RIFF"))
    }

    @Test
    fun recognize_omitsLanguageWhenAuto() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"hello"}"""))

        recognizer.recognize(request())

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("name=\"language\""))
    }

    @Test
    fun recognize_surfacesHttpFailure() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid key"))

        val failure = runCatching {
            runBlocking { recognizer.recognize(request()) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("HTTP 401"))
        assertTrue(failure?.message.orEmpty().contains("invalid key"))
    }

    @Test
    fun recognize_rejectsBlankText() {
        server.enqueue(MockResponse().setBody("""{"text":""}"""))

        val failure = runCatching {
            runBlocking { recognizer.recognize(request()) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("未返回文本"))
    }

    private fun request(language: String = "auto") = RecognitionRequest(
        pcm16 = byteArrayOf(1, 2, 3, 4),
        modelId = "gpt-4o-transcribe",
        apiKey = "secret",
        baseUrl = server.url("/v1").toString(),
        language = language,
    )
}
