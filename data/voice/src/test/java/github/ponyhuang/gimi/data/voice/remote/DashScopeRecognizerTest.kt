package github.ponyhuang.gimi.data.voice.remote

import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.voice.model.SpeechContextMessage
import github.ponyhuang.gimi.domain.voice.model.SpeechContextRole
import github.ponyhuang.gimi.domain.voice.repository.RecognitionEvent
import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
import github.ponyhuang.gimi.domain.voice.repository.VoiceHandshakeException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 百炼实时语音识别的协议边界。
 *
 * 重点覆盖三件事：请求里带的是不是正确的任务与音频参数、返回文本是否被正确解析、
 * 以及失败时音频不会被重放（重放会重复计费）。
 */
class DashScopeRecognizerTest {

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
    fun recognizeReturnsRecognizedTextAndSendsExpectedRunTask() = runTest {
        val controlEvents = mutableListOf<String>()
        var audioBytes = 0
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                controlEvents += text
                when (actionOf(text)) {
                    "run-task" -> {
                        webSocket.send(DashScopeTestEvents.taskStarted())
                        webSocket.send(DashScopeTestEvents.resultGenerated("今天天气不错"))
                    }
                    "finish-task" -> {
                        // 百炼严格要求结束指令携带空 input；缺失时会拒绝整个识别任务。
                        val payload = JsonParser.parseString(text).asJsonObject.getAsJsonObject("payload")
                        if (payload.get("input")?.isJsonObject == true) {
                            webSocket.send(DashScopeTestEvents.taskFinished())
                        } else {
                            webSocket.send(DashScopeTestEvents.taskFailed("InvalidParameter", "Missing payload.input"))
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                audioBytes += bytes.size
            }
        }))

        val text = recognizer().recognize(request(byteArrayOf(1, 2, 3, 4)))

        assertEquals("今天天气不错", text)
        assertEquals(4, audioBytes)

        val runTask = JsonParser.parseString(controlEvents.first()).asJsonObject
        val header = runTask.getAsJsonObject("header")
        assertEquals("run-task", header.get("action").asString)
        assertEquals("duplex", header.get("streaming").asString)
        val payload = runTask.getAsJsonObject("payload")
        assertEquals("audio", payload.get("task_group").asString)
        assertEquals("asr", payload.get("task").asString)
        assertEquals("recognition", payload.get("function").asString)
        assertEquals("qwen-audio-3.1-asr-flash-message", payload.get("model").asString)
        val parameters = payload.getAsJsonObject("parameters")
        assertEquals("pcm", parameters.get("format").asString)
        assertEquals(16_000, parameters.get("sample_rate").asInt)
        // 近讲麦克风要用近场 VAD，否则服务端按远场会议处理。
        assertEquals("near_meeting_16k", parameters.get("vad_model").asString)
        assertEquals(false, parameters.get("intermediate_result_enabled").asBoolean)
    }

    @Test
    fun streamingModelOmitsMessageOnlyParameters() = runTest {
        var runTaskJson = ""
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (actionOf(text)) {
                    "run-task" -> {
                        runTaskJson = text
                        webSocket.send(DashScopeTestEvents.taskStarted())
                        webSocket.send(DashScopeTestEvents.resultGenerated("今天天气不错"))
                    }
                    "finish-task" -> webSocket.send(DashScopeTestEvents.taskFinished())
                }
            }
        }))

        val text = recognizer().recognize(
            request(byteArrayOf(1), modelId = "qwen-audio-3.1-asr-flash-streaming"),
        )

        assertEquals("今天天气不错", text)
        val parameters = JsonParser.parseString(runTaskJson).asJsonObject
            .getAsJsonObject("payload").getAsJsonObject("parameters")
        // 流式模型没有这两个开关，多传会被服务端判为 InvalidParameter。
        assertTrue(parameters.get("intermediate_result_enabled") == null)
        assertTrue(parameters.get("disfluency_removal_enabled") == null)
        assertEquals("near_meeting_16k", parameters.get("vad_model").asString)
    }

    @Test
    fun recognizeSendsConversationContextWhenPresent() = runTest {
        var runTaskJson = ""
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (actionOf(text) == "run-task") {
                    runTaskJson = text
                    webSocket.send(DashScopeTestEvents.taskStarted())
                    webSocket.send(DashScopeTestEvents.resultGenerated("Gimi"))
                }
                if (actionOf(text) == "finish-task") webSocket.send(DashScopeTestEvents.taskFinished())
            }
        }))

        recognizer().recognize(
            request(
                byteArrayOf(1),
                context = listOf(
                    SpeechContextMessage(SpeechContextRole.User, "Gimi"),
                    SpeechContextMessage(SpeechContextRole.Assistant, "你好"),
                ),
            ),
        )

        val context = JsonParser.parseString(runTaskJson).asJsonObject
            .getAsJsonObject("payload").getAsJsonObject("input").getAsJsonArray("context")
        assertEquals(2, context.size())
        assertEquals("user", context[0].asJsonObject.get("role").asString)
        assertEquals(
            "input_text",
            context[0].asJsonObject.getAsJsonArray("content")[0].asJsonObject.get("type").asString,
        )
        assertEquals("assistant", context[1].asJsonObject.get("role").asString)
    }

    @Test
    fun recognizeDoesNotReplayAudioWhenTaskFails() = runTest {
        var audioMessages = 0
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (actionOf(text)) {
                    "run-task" -> {
                        webSocket.send(DashScopeTestEvents.taskStarted())
                        webSocket.send(DashScopeTestEvents.taskFailed("InvalidParameter", "参数错误"))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                audioMessages++
            }
        }))

        val error = runCatching {
            recognizer().recognize(request(ByteArray(64)))
        }.exceptionOrNull()

        assertTrue(error is DashScopeTaskException)
        assertEquals("InvalidParameter", (error as DashScopeTaskException).errorCode)
        // 失败后不得重放音频：语音识别按音频时长计费，重放会产生重复费用。
        assertEquals(1, audioMessages)
    }

    @Test
    fun recognizeSurfacesHandshakeAuthenticationFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = runCatching {
            recognizer().recognize(request(byteArrayOf(1)))
        }.exceptionOrNull()

        assertTrue(error is VoiceHandshakeException)
        assertEquals(401, (error as VoiceHandshakeException).statusCode)
    }

    @Test
    fun recognizeReportsEmptyResultAsFailure() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (actionOf(text)) {
                    "run-task" -> webSocket.send(DashScopeTestEvents.taskStarted())
                    "finish-task" -> webSocket.send(DashScopeTestEvents.taskFinished())
                }
            }
        }))

        val error = runCatching {
            recognizer().recognize(request(byteArrayOf(1)))
        }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
    }

    @Test
    fun streamingRecognizerEmitsPartialThenFinalResults() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (actionOf(text)) {
                    "run-task" -> {
                        webSocket.send(DashScopeTestEvents.taskStarted())
                        webSocket.send(DashScopeTestEvents.resultGenerated("今天", sentenceId = 1, sentenceEnd = false))
                        webSocket.send(DashScopeTestEvents.resultGenerated("今天天气不错", sentenceId = 1, sentenceEnd = true))
                    }
                    "finish-task" -> webSocket.send(DashScopeTestEvents.taskFinished())
                }
            }
        }))

        val events = streamingRecognizer().recognize(request(byteArrayOf(1, 2))).toList()

        assertEquals(2, events.size)
        assertEquals(RecognitionEvent.Partial("今天"), events[0])
        assertEquals(RecognitionEvent.Final("今天天气不错"), events[1])
    }

    private fun protocol() = DashScopeRecognitionProtocol(OkHttpClient())

    private fun recognizer() = DashScopeOneShotRecognizer(protocol())

    private fun streamingRecognizer() = DashScopeStreamingRecognizer(protocol())

    private fun request(
        pcm: ByteArray,
        context: List<SpeechContextMessage> = emptyList(),
        modelId: String = "qwen-audio-3.1-asr-flash-message",
    ) = RecognitionRequest(
        pcm16 = pcm,
        modelId = modelId,
        apiKey = "test-key",
        baseUrl = server.url("/api-ws/v1/inference").toString().replace("http://", "ws://"),
        context = context,
    )

    private fun actionOf(text: String): String =
        JsonParser.parseString(text).asJsonObject.getAsJsonObject("header").get("action").asString
}
