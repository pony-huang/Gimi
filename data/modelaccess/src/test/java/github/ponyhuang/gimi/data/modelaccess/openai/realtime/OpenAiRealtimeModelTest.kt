@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.modelaccess.openai.realtime

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
import kotlinx.coroutines.Dispatchers
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
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import github.ponyhuang.gimi.data.modelaccess.VoiceAudioMarkers
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeFunctionCallSink

/**
 * OpenAI Realtime 模型的 WebSocket 边界：握手后第一帧是 session.update、
 * 配置确认前音频被缓冲、下行事件按契约映射为 ADK 输出（音频内联块/双向转写/判轮标记）。
 */
class OpenAiRealtimeModelTest {

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

    private fun model(): OpenAiRealtimeModel = OpenAiRealtimeModel(
        // 与 DashScope 测试同源:MockWebServer 的 http URL 转 ws,避免 localhost 双栈解析与 TLS 误配。
        endpoint = server.url("/v1/realtime").toString().replace("http://", "ws://"),
        model = "gpt-realtime-2.1",
        apiKey = "sk-test",
        voice = "cedar",
        silenceDurationMs = OpenAiRealtimeProtocol.DEFAULT_SILENCE_DURATION_MS,
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
    fun `first control frame is session update with buffered audio flushed after ack`() = runTest {
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
        // 配置确认前的上行音频进入缓冲,确认后自动冲出。
        val pcm = ByteArray(16) { it.toByte() }
        connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=24000", data = pcm)))

        // 事件泵常驻收集(本用例的服务端不发下行事件);缓冲冲出靠泵处理 session.updated。
        val pump = launch(Dispatchers.IO) { runCatching { connection.receive().collect {} } }
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { while (audioAppends.isEmpty()) delay(10) }
        }
        assertTrue("缓冲音频未在配置确认后冲出", audioAppends.isNotEmpty())
        // 上行是 JSON 文本帧携带 base64 音频(OpenAI 协议),不是二进制帧。
        val decoded = java.util.Base64.getDecoder()
            .decode(JsonParser.parseString(audioAppends.first()).asJsonObject.get("audio").asString)
        assertTrue(pcm.contentEquals(decoded))

        val sessionUpdate = JsonParser.parseString(controlEvents.first()).asJsonObject
        assertEquals("session.update", sessionUpdate.get("type").asString)
        val session = sessionUpdate.getAsJsonObject("session")
        assertEquals("test persona", session.get("instructions").asString)
        assertEquals("cedar", session.get("voice").asString)
        assertEquals("server_vad", session.getAsJsonObject("audio").getAsJsonObject("input")
            .getAsJsonObject("turn_detection").get("type").asString)
        pump.cancel()
        connection.closeSession()
    }

    @Test
    fun `downstream events map to transcript markers audio parts and turn completion`() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("session.update")) {
                    webSocket.send("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"你好"}""")
                    webSocket.send("""{"type":"input_audio_buffer.committed","item_id":"u1"}""")
                    webSocket.send("""{"type":"response.output_audio.delta","delta":"AQID"}""")
                    webSocket.send("""{"type":"response.output_audio_transcript.delta","delta":"今天天气"}""")
                    webSocket.send("""{"type":"response.done","response":{"status":"completed"}}""")
                }
            }
        }))

        val connection = model().connect(connectRequest())
        val responses = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { connection.receive().take(5).toList() }
        }
        assertEquals(5, responses?.size)

        assertEquals("你好", responses!![0].inputTranscription?.text)
        assertTrue(responses[0].inputTranscription?.finished == true)

        assertTrue(responses[1].customMetadata?.get(VoiceAudioMarkers.TURN_COMMITTED) == true)

        val audioPart = responses[2].content?.parts?.singleOrNull()?.inlineData
        assertNotNull(audioPart)
        assertEquals("audio/pcm;rate=24000", audioPart?.mimeType)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(audioPart?.data))

        assertEquals("今天天气", responses[3].outputTranscription?.text)
        assertTrue(responses[4].turnComplete == true)
        connection.closeSession()
    }

    @Test
    fun `protocol error surfaces as error response and speech started triggers truncate`() = runTest {
        val controlEvents = mutableListOf<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(controlEvents) { controlEvents += text }
                when {
                    text.contains("session.update") -> {
                        webSocket.send("""{"type":"session.updated"}""")
                        webSocket.send(
                            """{"type":"response.output_item.added","item":{"type":"message","role":"assistant","id":"a1"}}""",
                        )
                        webSocket.send("""{"type":"response.output_audio.delta","delta":"AQID"}""")
                        webSocket.send("""{"type":"input_audio_buffer.speech_started"}""")
                        webSocket.send("""{"type":"response.done","response":{"status":"cancelled"}}""")
                    }
                }
            }
        }))

        val connection = model().connect(connectRequest())
        val responses = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { connection.receive().take(2).toList() }
        }
        // speech_started 时模型侧补发 truncate(带已发出时长);turnComplete 由 response.done 触发。
        assertEquals(2, responses?.size)
        assertTrue(responses!![1].turnComplete == true)
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(2_000) {
                while (controlEvents.none { it.contains("conversation.item.truncate") }) delay(10)
            }
        }
        val truncate = JsonParser.parseString(controlEvents.last { it.contains("conversation.item.truncate") }).asJsonObject
        assertEquals("a1", truncate.get("item_id").asString)
        assertEquals(0, truncate.get("content_index").asInt)
        assertEquals(0L, truncate.get("audio_end_ms").asLong)
        connection.closeSession()
    }

    @Test
    fun `server handshake failure is thrown to the collector for classification`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val connection = model().connect(connectRequest())
        val error = withContext(Dispatchers.IO) {
            runCatching { withTimeoutOrNull(5_000) { connection.receive().first() } }.exceptionOrNull()
        }
        assertNotNull(error)
        assertTrue(error is github.ponyhuang.gimi.domain.voice.repository.VoiceHandshakeException)
        assertEquals(401, (error as github.ponyhuang.gimi.domain.voice.repository.VoiceHandshakeException).statusCode)
        connection.closeSession()
    }

    @Test
    fun `function call finalizes into a response and writes back with a second round trigger`() = runTest {
        val controlEvents = mutableListOf<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(controlEvents) { controlEvents += text }
                if (text.contains("session.update")) {
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

        // 门面执行完工具后经回写通道写结果并触发二轮推理；两帧都必须按协议到达。
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
    fun `unfinished tool call arguments are flushed at response done`() = runTest {
        // 服务端未发 arguments.done（以 response.done 承载完整参数的兜底路径）：
        // 增量累积在响应结束时冲出，参数完整可用。
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("session.update")) {
                    webSocket.send("""{"type":"session.updated"}""")
                    webSocket.send("""{"type":"response.output_item.added","item":{"type":"function_call","call_id":"c9","name":"get_weather"}}""")
                    webSocket.send("""{"type":"response.function_call_arguments.delta","call_id":"c9","delta":"{\"city\":\"上海\"}"}""")
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
        assertEquals("c9", functionCall?.id)
        assertEquals("上海", functionCall?.args?.get("city"))
        assertTrue(responses[1].turnComplete == true)
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
        // barge-in：speech_started 取消生成后，累积中的参数随打断丢弃，后续正常完成
        // 的响应兜底冲出也不带回残留。
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
            withTimeoutOrNull(5_000) { connection.receive().take(2).toList() }
        }
        assertEquals(2, responses?.size)
        assertTrue(responses!!.all { it.content?.parts.orEmpty().none { part -> part.functionCall != null } })
        assertTrue(responses.all { it.turnComplete == true })
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
