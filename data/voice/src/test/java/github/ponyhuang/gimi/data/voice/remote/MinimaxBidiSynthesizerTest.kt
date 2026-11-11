package github.ponyhuang.gimi.data.voice.remote

import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.voice.repository.SynthesisSessionRequest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MiniMax 双向流式语音合成的协议边界。
 *
 * 覆盖开窗参数、增量文本投递、音频解码、片段字幕与协议级打断：
 * 合成按字符计费，任何"重放同一段文本"的行为都会变成重复费用。
 */
class MinimaxBidiSynthesizerTest {

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
    fun sessionStreamsSegmentsAndSendsExpectedParameters() = runTest {
        val received = mutableListOf<String>()
        var pending = ""
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send(connectedSuccess())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                received += text
                when (eventOf(text)) {
                    "task_start" -> webSocket.send(taskStarted())
                    "task_continue" -> {
                        // 服务端按标点累积：收到句末标点才开始合成这一句。
                        val buffer = pending + textOf(text)
                        pending = buffer
                        if (buffer.endsWith("。")) {
                            webSocket.send(sentenceStart())
                            webSocket.send(taskContinued("010203", buffer))
                            webSocket.send(sentenceEnd())
                            pending = ""
                        }
                    }
                    "task_flush" -> webSocket.send(taskFlushed())
                    "task_finish" -> {
                        webSocket.send(taskFinished())
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))

        val session = synthesizer().open(request())
        val segments = mutableListOf<Segment>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.segments.collect { segment ->
                val audio = segment.audio.toList()
                segments += Segment(segment.text.value, audio)
            }
        }
        // 文本按投递顺序逐次发出，服务端拿到半句就可以开始合成。
        session.send("你好")
        session.send("。")
        session.flush()
        collector.join()
        session.close()

        assertEquals(1, segments.size)
        assertEquals("你好。", segments[0].text)
        assertArrayEquals(byteArrayOf(1, 2, 3), segments[0].audio.single())

        val start = JsonParser.parseString(received.first()).asJsonObject
        assertEquals("task_start", start.get("event").asString)
        assertEquals("speech-2.8-hd", start.get("model").asString)
        assertEquals("male-qn-qingse", start.getAsJsonObject("voice_setting").get("voice_id").asString)
        val audio = start.getAsJsonObject("audio_setting")
        // 24 kHz 单声道 PCM 与播放器的 AudioTrack 一致，服务端不需要重采样。
        assertEquals("pcm", audio.get("format").asString)
        assertEquals(24_000, audio.get("sample_rate").asInt)
        assertEquals(1, audio.get("channel").asInt)
        assertTrue(start.get("subtitle_enable").asBoolean)

        val continues = received.map { JsonParser.parseString(it).asJsonObject }
            .filter { it.get("event").asString == "task_continue" }
        assertEquals(listOf("你好", "。"), continues.map { it.get("text").asString })
    }

    @Test
    fun cancelStopsSynthesisOnTheServerSide() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (eventOf(text)) {
                    "task_start" -> webSocket.send(taskStarted())
                    "task_cancel" -> {
                        cancelled.complete(Unit)
                        webSocket.send(taskCanceled())
                    }
                }
            }
        }))

        val session = synthesizer().open(request())
        session.send("打断前的内容")
        session.cancel()
        // runTest 的超时按虚拟时间计，等真实网络事件必须切到真实调度器。
        withContext(Dispatchers.IO) { withTimeout(5_000) { cancelled.await() } }
        session.close()
    }

    @Test
    fun taskFailureSurfacesTheVendorErrorCode() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (eventOf(text) == "task_start") {
                    webSocket.send(taskFailed(2013, "参数错误"))
                }
            }
        }))

        val session = synthesizer().open(request())
        val error = runCatching {
            session.send("内容")
            session.flush()
        }.exceptionOrNull()

        assertTrue(error is TtsSynthesisException)
        assertEquals(2013, (error as TtsSynthesisException).statusCode)
        session.close()
    }

    private fun synthesizer() = MinimaxBidiSynthesizer(
        OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
    )

    private fun request() = SynthesisSessionRequest(
        voiceId = "male-qn-qingse",
        modelId = "speech-2.8-hd",
        apiKey = "test-key",
        baseUrl = server.url("/v1").toString(),
    )

    private fun eventOf(text: String): String =
        JsonParser.parseString(text).asJsonObject.get("event").asString

    private fun textOf(text: String): String =
        JsonParser.parseString(text).asJsonObject.get("text").asString

    private fun connectedSuccess(): String =
        """{"event":"connected_success","session_id":"s-1","base_resp":{"status_code":0,"status_msg":"success"}}"""

    private fun taskStarted(): String =
        """{"event":"task_started","session_id":"s-1","base_resp":{"status_code":0,"status_msg":"success"}}"""

    private fun sentenceStart(): String =
        """{"event":"sentence_start","session_id":"s-1","base_resp":{"status_code":0,"status_msg":"success"}}"""

    private fun taskContinued(audioHex: String, subtitle: String): String =
        """{"event":"task_continued","session_id":"s-1","is_final":false,
            "data":{"audio":"$audioHex","subtitle":{"text":"$subtitle","text_begin":0,"text_end":3,
            "time_begin":0,"time_end":800}},
            "base_resp":{"status_code":0,"status_msg":"success"}}"""

    private fun sentenceEnd(): String =
        """{"event":"sentence_end","session_id":"s-1","base_resp":{"status_code":0,"status_msg":"success"}}"""

    private fun taskFlushed(): String =
        """{"event":"task_flushed","session_id":"s-1","base_resp":{"status_code":0,"status_msg":"success"}}"""

    private fun taskCanceled(): String =
        """{"event":"task_canceled","session_id":"s-1","base_resp":{"status_code":0,"status_msg":"success"}}"""

    private fun taskFinished(): String =
        """{"event":"task_finished","session_id":"s-1","base_resp":{"status_code":0,"status_msg":"success"}}"""

    private fun taskFailed(code: Int, message: String): String =
        """{"event":"task_failed","session_id":"s-1","base_resp":{"status_code":$code,"status_msg":"$message"}}"""

    private data class Segment(val text: String, val audio: List<ByteArray>)
}
