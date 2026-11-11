package github.ponyhuang.gimi.data.voice.remote

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

/** MiniMax 识别的 multipart 上传契约。 */
class MinimaxRecognizerTest {
    private lateinit var server: MockWebServer
    private lateinit var recognizer: MinimaxRecognizer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        recognizer = MinimaxRecognizer(
            MinimaxSpeechProtocol(
                httpClient = OkHttpClient(),
                sttEndpoint = server.url("/v1/speech_to_text").toString(),
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun recognize_uploadsWavUsingMinimaxMultipartContract() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"  你好，MiniMax。  ","duration":1.2}"""))

        val result = recognizer.recognize(request(language = "zh"))

        assertEquals("  你好，MiniMax。  ", result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/speech_to_text", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertEquals("zh", recorded.getHeader("language"))
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("asr-1.0"))
        assertTrue(body.contains("name=\"response_format\""))
        assertTrue(body.contains("json"))
        assertTrue(body.contains("name=\"stream\""))
        assertTrue(body.contains("false"))
        assertTrue(body.contains("filename=\"recording.wav\""))
        assertTrue(body.contains("RIFF"))
    }

    @Test
    fun recognize_usesMixedLanguageRecognitionWhenLanguageIsAuto() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"hello"}"""))

        recognizer.recognize(request())

        assertFalse(server.takeRequest().headers.names().any { it.equals("language", ignoreCase = true) })
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

    private fun request(language: String = "auto") = RecognitionRequest(
        pcm16 = byteArrayOf(1, 2, 3, 4),
        modelId = "asr-1.0",
        apiKey = "secret",
        baseUrl = "https://api.minimaxi.com/v1",
        language = language,
    )
}
