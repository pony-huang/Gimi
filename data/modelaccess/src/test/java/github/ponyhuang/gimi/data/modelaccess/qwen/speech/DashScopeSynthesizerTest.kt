package github.ponyhuang.gimi.data.modelaccess.qwen.speech

import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 百炼实时语音合成的协议边界：请求参数、二进制音频帧解析，以及失败时不重放文本。 */
class DashScopeSynthesizerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun synthesizeStreamsAudioFramesAndSendsExpectedParameters() = runTest {
        val controlEvents = mutableListOf<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                controlEvents += text
                when (actionOf(text)) {
                    "run-task" -> webSocket.send(DashScopeTestEvents.taskStarted())
                    "continue-task" -> {
                        webSocket.send(DashScopeTestEvents.sentenceSynthesis())
                        webSocket.send(byteArrayOf(1, 2, 3).toByteString())
                        webSocket.send(byteArrayOf(4, 5).toByteString())
                    }
                    "finish-task" -> {
                        val payload = JsonParser.parseString(text).asJsonObject.getAsJsonObject("payload")
                        if (payload.get("input")?.isJsonObject == true) {
                            webSocket.send(DashScopeTestEvents.taskFinished())
                        } else {
                            webSocket.send(DashScopeTestEvents.taskFailed("InvalidParameter", "Missing payload.input"))
                        }
                    }
                }
            }
        }))

        val audio = synthesizer().synthesize(request()).toList()

        assertEquals(2, audio.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), audio[0])
        assertArrayEquals(byteArrayOf(4, 5), audio[1])

        val runTask = JsonParser.parseString(controlEvents.first()).asJsonObject
        val payload = runTask.getAsJsonObject("payload")
        assertEquals("tts", payload.get("task").asString)
        assertEquals("SpeechSynthesizer", payload.get("function").asString)
        assertEquals("qwen-audio-3.1-tts-flash", payload.get("model").asString)
        val parameters = payload.getAsJsonObject("parameters")
        assertEquals("PlainText", parameters.get("text_type").asString)
        assertEquals("longanlingxin_v3.1", parameters.get("voice").asString)
        // 24 kHz 单声道 PCM 与播放器的 AudioTrack 一致，服务端不需要重采样。
        assertEquals("pcm", parameters.get("format").asString)
        assertEquals(24_000, parameters.get("sample_rate").asInt)

        val continueTask = JsonParser.parseString(controlEvents[1]).asJsonObject
        assertEquals("床前明月光", continueTask.getAsJsonObject("payload")
            .getAsJsonObject("input").get("text").asString)
        // 同一任务的事件必须共用一个 task_id。
        assertEquals(
            runTask.getAsJsonObject("header").get("task_id").asString,
            continueTask.getAsJsonObject("header").get("task_id").asString,
        )
    }

    @Test
    fun synthesizeFailsWhenTaskFails() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (actionOf(text) == "run-task") {
                    webSocket.send(DashScopeTestEvents.taskStarted())
                    webSocket.send(DashScopeTestEvents.taskFailed("InvalidParameter", "音色不存在"))
                }
            }
        }))

        val error = runCatching {
            synthesizer().synthesize(request()).toList()
        }.exceptionOrNull()

        assertTrue(error is DashScopeTaskException)
        assertEquals("InvalidParameter", (error as DashScopeTaskException).errorCode)
    }

    @Test
    fun synthesizeFailsWhenNoAudioArrives() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (actionOf(text)) {
                    "run-task" -> webSocket.send(DashScopeTestEvents.taskStarted())
                    "finish-task" -> webSocket.send(DashScopeTestEvents.taskFinished())
                }
            }
        }))

        val error = runCatching {
            synthesizer().synthesize(request()).toList()
        }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
    }

    private fun synthesizer() = DashScopeSynthesizer(OkHttpClient())

    private fun request() = SynthesisRequest(
        text = "床前明月光",
        voiceId = "longanlingxin_v3.1",
        modelId = "qwen-audio-3.1-tts-flash",
        apiKey = "test-key",
        baseUrl = server.url("/api-ws/v1/inference").toString().replace("http://", "ws://"),
    )

    private fun actionOf(text: String): String =
        JsonParser.parseString(text).asJsonObject.getAsJsonObject("header").get("action").asString
}
