@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.RealtimeInput
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Tool
import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.voice.repository.VoiceHandshakeException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Qwen Realtime 模型的 WebSocket 边界：握手后第一帧是 session.update（百炼结构）、
 * 配置确认前 16k 音频被缓冲、下行事件映射为 ADK 输出（音频内联块/双向转写/判轮标记）、
 * 打断走静默窗口（不发截断事件）。
 *
 * 同时覆盖 [LiveConnectionContractTest] 中适用于全双工连接的契约子集：
 * 单接收者强制与 close 解锁等待方（文字轮契约不适用——Realtime 连接拒绝文本输入）。
 */
class QwenRealtimeModelTest {

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

    private fun model(): QwenRealtimeModel = QwenRealtimeModel(
        // 与 DashScope 测试同源:MockWebServer 的 http URL 转 ws,避免 localhost 双栈解析与 TLS 误配。
        endpoint = server.url("/api-ws/v1/realtime").toString().replace("http://", "ws://"),
        model = "qwen-audio-3.1-realtime-plus",
        apiKey = "sk-test",
        voice = "longanqian_v3.1",
        silenceDurationMs = QwenRealtimeProtocol.DEFAULT_SILENCE_DURATION_MS,
        workspaceId = null,
        webSocketClient = OkHttpClient(),
    )

    private fun connectRequest(
        tools: List<FunctionDeclaration> = emptyList(),
    ) = LlmRequest(
        model = null,
        config = GenerateContentConfig(
            systemInstruction = Content.fromText(Role.SYSTEM, "test persona"),
            tools = tools.takeUnless { it.isEmpty() }?.let { listOf(Tool(functionDeclarations = it)) },
        ),
    )

    @Test
    fun `first control frame is dashscope session update with buffered 16k audio flushed after ack`() = runTest {
        val controlEvents = mutableListOf<String>()
        val audioAppends = mutableListOf<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(controlEvents) { controlEvents += text }
                when {
                    text.contains("session.update") -> webSocket.send("""{"type":"session.updated"}""")
                    text.contains("input_audio_buffer.append") -> synchronized(audioAppends) { audioAppends += text }
                }
            }
        }))

        val connection = model().connect(connectRequest())
        // 配置确认前的上行音频进入缓冲,确认后自动冲出;Qwen 原生 16k 直喂。
        val pcm = ByteArray(16) { it.toByte() }
        connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=16000", data = pcm)))

        val pump = launch(Dispatchers.IO) { runCatching { connection.receive().collect {} } }
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { while (audioAppends.isEmpty()) delay(10) }
        }
        assertTrue("缓冲音频未在配置确认后冲出", audioAppends.isNotEmpty())
        val decoded = java.util.Base64.getDecoder()
            .decode(JsonParser.parseString(audioAppends.first()).asJsonObject.get("audio").asString)
        assertTrue(pcm.contentEquals(decoded))

        val sessionUpdate = JsonParser.parseString(controlEvents.first()).asJsonObject
        assertEquals("session.update", sessionUpdate.get("type").asString)
        val session = sessionUpdate.getAsJsonObject("session")
        assertEquals("test persona", session.get("instructions").asString)
        assertEquals("longanqian_v3.1", session.get("voice").asString)
        assertEquals("pcm", session.get("input_audio_format").asString)
        assertEquals("pcm", session.get("output_audio_format").asString)
        assertEquals("server_vad", session.getAsJsonObject("turn_detection").get("type").asString)
        pump.cancel()
        connection.closeSession()
    }

    @Test
    fun `downstream events map to transcripts markers audio parts and turn completion`() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("session.update")) {
                    // 用户字幕:增量(已确定文本)先到,终稿替换。
                    webSocket.send(
                        """{"type":"conversation.item.input_audio_transcription.delta","text":"你","stash":"好"}""",
                    )
                    webSocket.send("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"你好"}""")
                    webSocket.send("""{"type":"input_audio_buffer.committed","item_id":"u1"}""")
                    webSocket.send("""{"type":"response.audio.delta","delta":"AQID"}""")
                    webSocket.send("""{"type":"response.audio_transcript.delta","delta":"今天天气"}""")
                    webSocket.send("""{"type":"response.audio_transcript.done","transcript":"今天天气很好"}""")
                    webSocket.send("""{"type":"response.done","response":{"status":"completed"}}""")
                }
            }
        }))

        val connection = model().connect(connectRequest())
        val responses = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { connection.receive().take(6).toList() }
        }
        assertEquals(6, responses?.size)

        // 用户字幕增量与终稿都经 inputTranscription 通道;编排层按替换语义渲染。
        assertEquals("你", responses!![0].inputTranscription?.text)
        assertEquals("你好", responses[1].inputTranscription?.text)
        assertTrue(responses[1].inputTranscription?.finished == true)

        assertTrue(responses[2].customMetadata?.get(VoiceAudioMarkers.TURN_COMMITTED) == true)

        val audioPart = responses[3].content?.parts?.singleOrNull()?.inlineData
        assertNotNull(audioPart)
        assertEquals("audio/pcm;rate=24000", audioPart?.mimeType)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(audioPart?.data))

        assertEquals("今天天气", responses[4].outputTranscription?.text)
        // 已有增量时 transcript.done 被忽略(追加语义防重复),turnComplete 由 response.done 触发。
        assertTrue(responses[5].turnComplete == true)
        connection.closeSession()
    }

    @Test
    fun `silent window drops residual audio until next response`() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("session.update")) {
                    webSocket.send("""{"type":"response.created"}""")
                    webSocket.send("""{"type":"response.audio.delta","delta":"AQID"}""")
                    webSocket.send("""{"type":"input_audio_buffer.speech_started"}""")
                    webSocket.send("""{"type":"response.audio.delta","delta":"AQID"}""")
                    webSocket.send("""{"type":"response.created"}""")
                    webSocket.send("""{"type":"response.audio.delta","delta":"AQID"}""")
                }
            }
        }))

        val connection = model().connect(connectRequest())
        val responses = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { connection.receive().take(3).toList() }
        }
        // 打断不发任何截断/取消事件;静默窗口内残余音频增量被丢弃,新响应开始后恢复。
        assertEquals(3, responses?.size)
        assertNotNull(responses!![0].content?.parts?.singleOrNull()?.inlineData)
        assertTrue(responses[1].interrupted == true)
        assertNotNull(responses[2].content?.parts?.singleOrNull()?.inlineData)
        connection.closeSession()
    }

    @Test
    fun `transcript done without delta is emitted as final increment`() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("session.update")) {
                    // 第一条响应:增量 + 终稿(done 被抑制)。
                    webSocket.send("""{"type":"response.created"}""")
                    webSocket.send("""{"type":"response.audio_transcript.delta","delta":"哈"}""")
                    webSocket.send("""{"type":"response.audio_transcript.done","transcript":"哈哈"}""")
                    // 第二条响应:只有终稿(补发为增量)。
                    webSocket.send("""{"type":"response.created"}""")
                    webSocket.send("""{"type":"response.audio_transcript.done","transcript":"很好"}""")
                }
            }
        }))

        val connection = model().connect(connectRequest())
        val responses = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { connection.receive().take(2).toList() }
        }
        assertEquals(2, responses?.size)
        assertEquals("哈", responses!![0].outputTranscription?.text)
        assertEquals("很好", responses[1].outputTranscription?.text)
        connection.closeSession()
    }

    @Test
    fun `close unblocks waiting receiver and concurrent receivers are rejected`() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            // 服务端不发任何帧;等待方靠 closeSession 解锁。
            override fun onMessage(webSocket: WebSocket, text: String) = Unit
        }))
        val connection = model().connect(connectRequest())
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            // VoiceWsKernel.close() 以 socket.cancel() 硬断连接,等待方以传输异常收尾,
            // 与级联假传输的「空流收尾」不同;这里只验证契约核心:等待方必须被解锁。
            runCatching { connection.receive().toList() }
        }
        try {
            connection.receive().toList()
            fail("Expected single receiver enforcement")
        } catch (_: IllegalStateException) {
            // 并发接收必须失败,不能让两名消费者分走同一条连接的帧。
        } finally {
            connection.closeSession()
        }
        val settled = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { waiting.await() }
        }
        assertNotNull("closeSession 应解锁等待中的接收方", settled)
    }

    @Test
    fun `text turn input is rejected`() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = Unit
        }))
        val connection = model().connect(connectRequest())
        try {
            connection.sendContent(Content.fromText(Role.USER, "文本轮"), partial = false)
            fail("Realtime 全双工会话应拒绝文本轮输入")
        } catch (_: UnsupportedOperationException) {
            // 用户输入以音频直喂,文本轮留阶段二。
        } finally {
            connection.closeSession()
        }
    }

    @Test
    fun `server handshake failure is thrown to the collector for classification`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val connection = model().connect(connectRequest())
        val error = withContext(Dispatchers.IO) {
            runCatching { withTimeoutOrNull(5_000) { connection.receive().first() } }.exceptionOrNull()
        }
        assertNotNull(error)
        assertTrue(error is VoiceHandshakeException)
        assertEquals(401, (error as VoiceHandshakeException).statusCode)
        connection.closeSession()
    }

    @Test
    fun `function call finalizes into a response and writes back with a second round trigger`() = runTest {
        val controlEvents = mutableListOf<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(controlEvents) { controlEvents += text }
                if (text.contains("session.update")) {
                    // 官方文档的 function_call 事件序：起始项 → 参数增量 → 参数定稿 → 响应结束。
                    webSocket.send("""{"type":"session.updated"}""")
                    webSocket.send("""{"type":"response.output_item.added","item":{"type":"function_call","call_id":"c1","name":"get_weather"}}""")
                    webSocket.send("""{"type":"response.function_call_arguments.delta","call_id":"c1","delta":"{\"city\":"}""")
                    webSocket.send("""{"type":"response.function_call_arguments.done","call_id":"c1","name":"get_weather","arguments":"{\"city\":\"杭州\"}"}""")
                    webSocket.send("""{"type":"response.done","response":{"status":"completed"}}""")
                }
            }
        }))

        val connection = model().connect(connectRequest(tools = listOf(weatherDeclaration())))
        val responses = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { connection.receive().take(2).toList() }
        }
        assertEquals(2, responses?.size)
        val functionCall = responses!![0].content?.parts?.singleOrNull()?.functionCall
        assertNotNull(functionCall)
        assertEquals("get_weather", functionCall?.name)
        assertEquals("c1", functionCall?.id)
        assertEquals("杭州", functionCall?.args?.get("city"))
        assertTrue(responses[1].turnComplete == true)

        // 官方步骤 3/4：function_call_output 经 conversation.item.create 写回，再 response.create 触发二轮。
        val sink = connection as RealtimeFunctionCallSink
        sink.sendFunctionResult("c1", """{"temperature":18}""")
        sink.sendResponseTrigger()
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(2_000) {
                while (controlEvents.none { it.contains("function_call_output") } ||
                    controlEvents.none { it.contains("response.create") }) delay(10)
            }
        }
        val output = JsonParser.parseString(controlEvents.last { it.contains("function_call_output") }).asJsonObject
        assertEquals("conversation.item.create", output.get("type").asString)
        val item = output.getAsJsonObject("item")
        assertEquals("function_call_output", item.get("type").asString)
        assertEquals("c1", item.get("call_id").asString)
        assertEquals("""{"temperature":18}""", item.get("output").asString)
        val trigger = JsonParser.parseString(controlEvents.last { it.contains("response.create") }).asJsonObject
        assertEquals("response.create", trigger.get("type").asString)
        connection.closeSession()
    }

    @Test
    fun `cancelled response discards unfinished tool calls without leaking into the next flush`() = runTest {
        // 打断竞态：响应 A 的 function_call 参数尚未 done 就被取消，残缺累积不得冲出
        // 执行，也不得泄入响应 B 的兜底冲出（否则以截断参数执行或为取消调用写回）。
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("session.update")) {
                    webSocket.send("""{"type":"session.updated"}""")
                    // 响应 A：参数只累积到一半就被取消。
                    webSocket.send("""{"type":"response.output_item.added","item":{"type":"function_call","call_id":"c1","name":"get_weather"}}""")
                    webSocket.send("""{"type":"response.function_call_arguments.delta","call_id":"c1","delta":"{\"city\":"}""")
                    webSocket.send("""{"type":"response.done","response":{"status":"cancelled"}}""")
                    // 响应 B：参数完整但走兜底冲出（无 arguments.done）。
                    webSocket.send("""{"type":"response.output_item.added","item":{"type":"function_call","call_id":"c2","name":"get_weather"}}""")
                    webSocket.send("""{"type":"response.function_call_arguments.delta","call_id":"c2","delta":"{\"city\":\"上海\"}"}""")
                    webSocket.send("""{"type":"response.done","response":{"status":"completed"}}""")
                }
            }
        }))

        val connection = model().connect(connectRequest(tools = listOf(weatherDeclaration())))
        val responses = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { connection.receive().take(3).toList() }
        }
        assertEquals(3, responses?.size)
        // 取消的响应 A 自身仍以 turnComplete 收尾（既有语义），其后只有响应 B 的调用被冲出。
        assertTrue(responses!![0].turnComplete == true)
        val functionCall = responses[1].content?.parts?.singleOrNull()?.functionCall
        assertNotNull(functionCall)
        assertEquals("c2", functionCall?.id)
        assertEquals("上海", functionCall?.args?.get("city"))
        assertTrue(responses[2].turnComplete == true)
        connection.closeSession()
    }

    @Test
    fun `speech started discards pending tool call arguments`() = runTest {
        // barge-in：speech_started 打断后累积中的参数随打断丢弃，后续正常完成的响应
        // 兜底冲出也不带回残留。
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("session.update")) {
                    webSocket.send("""{"type":"session.updated"}""")
                    webSocket.send("""{"type":"response.output_item.added","item":{"type":"function_call","call_id":"c1","name":"get_weather"}}""")
                    webSocket.send("""{"type":"response.function_call_arguments.delta","call_id":"c1","delta":"{\"city\":"}""")
                    webSocket.send("""{"type":"input_audio_buffer.speech_started"}""")
                    webSocket.send("""{"type":"response.done","response":{"status":"cancelled"}}""")
                    webSocket.send("""{"type":"response.done","response":{"status":"completed"}}""")
                }
            }
        }))

        val connection = model().connect(connectRequest(tools = listOf(weatherDeclaration())))
        val responses = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { connection.receive().take(3).toList() }
        }
        assertEquals(3, responses?.size)
        // speech_started 的打断通知在前；两条 response.done 只产出 turnComplete，无任何 functionCall。
        assertTrue(responses!![0].interrupted == true)
        assertTrue(responses.all { it.content?.parts.orEmpty().none { part -> part.functionCall != null } })
        assertTrue(responses.drop(1).all { it.turnComplete == true })
        connection.closeSession()
    }

    private companion object {
        /** 测试工具声明：与协议 sessionUpdate 工具形状用例共用一份参数结构。 */
        fun weatherDeclaration() = FunctionDeclaration(
            name = "get_weather",
            description = "Query city weather",
            parameters = Schema(
                type = com.google.adk.kt.types.Type.OBJECT,
                properties = mapOf("city" to Schema(type = com.google.adk.kt.types.Type.STRING)),
                required = listOf("city"),
            ),
        )
    }
}
