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
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioMarkers
import github.ponyhuang.gimi.domain.voice.repository.RealtimeFunctionCallSink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeTurnControlSink

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
        assertTrue(session.getAsJsonObject("audio").getAsJsonObject("input")
            .get("turn_detection").isJsonNull)
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
    fun `local turn control sends append commit and response create in order`() = runTest {
        val controls = java.util.concurrent.CopyOnWriteArrayList<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = JsonParser.parseString(text).asJsonObject.get("type").asString
                controls += type
                if (type == "session.update") webSocket.send("""{"type":"session.updated"}""")
            }
        }))
        val connection = model().connect(connectRequest())
        val pump = launch(Dispatchers.IO) { runCatching { connection.receive().collect {} } }
        try {
            connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=24000", data = ByteArray(16))))
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while ("input_audio_buffer.append" !in controls) delay(10) }
            }
            val sink = connection as RealtimeTurnControlSink
            sink.commitAudioTurn()
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while ("response.create" !in controls) delay(10) }
            }
            assertEquals(
                listOf("session.update", "input_audio_buffer.append", "input_audio_buffer.commit", "response.create"),
                controls.toList(),
            )
        } finally {
            connection.closeSession()
            pump.cancel()
        }
    }

    @Test
    fun `commit before session ack waits for buffered audio to flush first`() = runTest {
        val controls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val socket = java.util.concurrent.atomic.AtomicReference<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                controls += JsonParser.parseString(text).asJsonObject.get("type").asString
                if (text.contains("session.update")) socket.set(webSocket)
            }
        }))
        val connection = model().connect(connectRequest())
        val pump = launch(Dispatchers.IO) { runCatching { connection.receive().collect {} } }
        try {
            connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=24000", data = ByteArray(16))))
            val commit = async { (connection as RealtimeTurnControlSink).commitAudioTurn() }
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while (socket.get() == null) delay(10) }
                delay(100)
            }
            assertTrue("配置确认前不应提交音频", "input_audio_buffer.commit" !in controls)
            socket.get().send("""{"type":"session.updated"}""")
            withContext(Dispatchers.IO) { withTimeoutOrNull(5_000) { commit.await() } }
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while ("response.create" !in controls) delay(10) }
            }
            assertEquals(
                listOf("session.update", "input_audio_buffer.append", "input_audio_buffer.commit", "response.create"),
                controls.toList(),
            )
        } finally {
            connection.closeSession()
            pump.cancel()
        }
    }

    @Test
    fun `barge in before response created cancels the requested response and suppresses its audio`() = runTest {
        val controls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val socket = java.util.concurrent.atomic.AtomicReference<WebSocket>()
        val emittedAudio = java.util.concurrent.atomic.AtomicInteger()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = JsonParser.parseString(text).asJsonObject.get("type").asString
                controls += type
                when (type) {
                    "session.update" -> {
                        socket.set(webSocket)
                        webSocket.send("""{"type":"session.updated"}""")
                    }
                    "response.cancel" -> {
                        webSocket.send("""{"type":"response.output_audio.delta","delta":"AQID"}""")
                        webSocket.send("""{"type":"response.done","response":{"status":"cancelled"}}""")
                    }
                }
            }
        }))
        val connection = model().connect(connectRequest())
        val pump = launch(Dispatchers.IO) {
            runCatching {
                connection.receive().collect {
                    if (it.content != null) emittedAudio.incrementAndGet()
                    if (it.turnComplete == true) completed.set(true)
                }
            }
        }
        try {
            val sink = connection as RealtimeTurnControlSink
            sink.commitAudioTurn()
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while (socket.get() == null) delay(10) }
            }
            sink.cancelResponse()
            assertEquals("created 前不应提前取消", 0, controls.count { it == "response.cancel" })
            socket.get().send("""{"type":"response.created"}""")
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while (!completed.get()) delay(10) }
            }
            assertEquals(1, controls.count { it == "response.cancel" })
            assertEquals(0, emittedAudio.get())

            // 取消窗口不能污染下一轮合法响应。
            socket.get().send("""{"type":"response.created"}""")
            socket.get().send("""{"type":"response.output_audio.delta","delta":"AQID"}""")
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while (emittedAudio.get() == 0) delay(10) }
            }
            assertEquals(1, emittedAudio.get())
        } finally {
            connection.closeSession()
            pump.cancel()
        }
    }

    @Test
    fun `local barge in cancels and truncates an active response`() = runTest {
        val controls = java.util.concurrent.CopyOnWriteArrayList<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = JsonParser.parseString(text).asJsonObject.get("type").asString
                controls += type
                if (type == "session.update") {
                    webSocket.send("""{"type":"session.updated"}""")
                    webSocket.send("""{"type":"response.created"}""")
                    webSocket.send("""{"type":"response.output_item.added","item":{"type":"function_call","call_id":"c1","name":"get_weather"}}""")
                    webSocket.send("""{"type":"response.function_call_arguments.delta","call_id":"c1","delta":"{\"city\":"}""")
                    webSocket.send("""{"type":"response.output_item.added","item":{"type":"message","role":"assistant","id":"a1"}}""")
                    webSocket.send("""{"type":"response.output_audio.delta","delta":"AQID"}""")
                }
                if (type == "response.cancel") {
                    webSocket.send("""{"type":"response.function_call_arguments.done","call_id":"c1","name":"get_weather","arguments":"{\"city\":\"杭州\"}"}""")
                    webSocket.send("""{"type":"response.output_audio.delta","delta":"AQID"}""")
                    webSocket.send("""{"type":"response.done","response":{"status":"cancelled"}}""")
                }
            }
        }))
        val connection = model().connect(connectRequest(tools = listOf(weatherDeclaration())))
        val received = java.util.concurrent.atomic.AtomicBoolean(false)
        val receivedAudioCount = java.util.concurrent.atomic.AtomicInteger()
        val lateToolCalls = java.util.concurrent.atomic.AtomicInteger()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val pump = launch(Dispatchers.IO) {
            runCatching { connection.receive().collect {
                if (it.content != null) {
                    received.set(true)
                    receivedAudioCount.incrementAndGet()
                    if (it.content?.parts.orEmpty().any { part -> part.functionCall != null }) lateToolCalls.incrementAndGet()
                }
                if (it.turnComplete == true) completed.set(true)
            } }
        }
        try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while (!received.get()) delay(10) }
            }
            assertTrue(received.get())
            val beforeCancel = receivedAudioCount.get()
            val sink = connection as RealtimeTurnControlSink
            sink.cancelResponse()
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while ("conversation.item.truncate" !in controls || !completed.get()) delay(10) }
            }
            assertEquals(listOf("response.cancel", "conversation.item.truncate"), controls.drop(1))
            assertEquals("取消后的残余音频不得上抛", beforeCancel, receivedAudioCount.get())
            assertEquals("取消后才定稿的工具调用不得执行", 0, lateToolCalls.get())
        } finally {
            connection.closeSession()
            pump.cancel()
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
                    webSocket.send("""{"type":"response.created"}""")
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

    @Test
    fun manualSubmissionCancelsAndSuppressesOldResponseUntilNextCreated() = runTest {
        val controls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val transcripts = java.util.concurrent.CopyOnWriteArrayList<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = JsonParser.parseString(text).asJsonObject.get("type").asString
                controls += type
                when (type) {
                    "session.update" -> {
                        webSocket.send("""{"type":"session.updated"}""")
                        webSocket.send("""{"type":"response.created"}""")
                        webSocket.send("""{"type":"response.output_item.added","item":{"type":"message","role":"assistant","id":"old-answer"}}""")
                        webSocket.send("""{"type":"response.output_audio_transcript.delta","delta":"旧回答"}""")
                    }
                    "response.cancel" -> {
                        webSocket.send("""{"type":"response.output_audio_transcript.delta","delta":"旧尾音"}""")
                        webSocket.send("""{"type":"response.done","response":{"status":"cancelled"}}""")
                    }
                    "response.create" -> {
                        webSocket.send("""{"type":"response.created"}""")
                        webSocket.send("""{"type":"response.output_audio_transcript.delta","delta":"新回答"}""")
                    }
                }
            }
        }))
        val connection = model().connect(connectRequest())
        val pump = launch(Dispatchers.IO) {
            runCatching { connection.receive().collect { it.outputTranscription?.text?.let(transcripts::add) } }
        }
        try {
            val ready = withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while ("旧回答" !in transcripts) delay(10); true }
            }
            assertEquals(true, ready)
            val sink = connection as github.ponyhuang.gimi.data.modelaccess.shared.RealtimeManualInputSink
            sink.submitUserContent(Content.fromText(Role.USER, "改成一句话"))
            val answered = withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while ("新回答" !in transcripts) delay(10); true }
            }
            assertEquals(true, answered)
            assertEquals(listOf("旧回答", "新回答"), transcripts.toList())
            assertEquals(listOf("response.cancel", "conversation.item.truncate", "input_audio_buffer.clear",
                "conversation.item.create", "response.create"), controls.drop(1))
        } finally {
            connection.closeSession()
            pump.cancel()
        }
    }

    @Test
    fun manualSubmissionSuppressesResponseCreatedAfterItWasRequested() = runTest {
        val controls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val transcripts = java.util.concurrent.CopyOnWriteArrayList<String>()
        val socket = java.util.concurrent.atomic.AtomicReference<WebSocket>()
        var responseCreates = 0
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = JsonParser.parseString(text).asJsonObject.get("type").asString
                controls += type
                when (type) {
                    "session.update" -> {
                        socket.set(webSocket)
                        webSocket.send("""{"type":"session.updated"}""")
                    }
                    "response.create" -> {
                        responseCreates++
                        if (responseCreates == 2) {
                            webSocket.send("""{"type":"response.created","response":{"id":"old"}}""")
                        }
                    }
                    "response.cancel" -> {
                        webSocket.send("""{"type":"response.output_audio_transcript.delta","delta":"旧尾音"}""")
                        webSocket.send("""{"type":"response.done","response":{"status":"cancelled"}}""")
                    }
                }
            }
        }))
        val connection = model().connect(connectRequest())
        val pump = launch(Dispatchers.IO) {
            runCatching { connection.receive().collect { it.outputTranscription?.text?.let(transcripts::add) } }
        }
        try {
            val sink = connection as RealtimeTurnControlSink
            sink.commitAudioTurn()
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while ("response.create" !in controls) delay(10) }
            }
            val manual = connection as github.ponyhuang.gimi.data.modelaccess.shared.RealtimeManualInputSink
            manual.submitUserContent(Content.fromText(Role.USER, "改成一句话"))
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while (controls.count { it == "response.create" } < 2) delay(10) }
            }
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while (controls.none { it == "response.cancel" }) delay(10) }
            }
            assertEquals("延迟 created 的旧响应必须被取消", 1, controls.count { it == "response.cancel" })

            sink.cancelResponse()
            socket.get().send("""{"type":"response.created","response":{"id":"new"}}""")
            socket.get().send("""{"type":"response.output_audio_transcript.delta","delta":"新回答"}""")
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) { while (controls.count { it == "response.cancel" } < 2) delay(10) }
            }
            assertEquals("手动输入的待确认响应也必须可被本地打断取消", 2, controls.count { it == "response.cancel" })
            assertEquals(emptyList<String>(), transcripts.toList())
        } finally {
            connection.closeSession()
            pump.cancel()
        }
    }

    @Test
    fun `sendHistory buffers and flushes conversation items before audio`() = runTest {
        val sentFrames = java.util.concurrent.CopyOnWriteArrayList<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                sentFrames += text
                if (text.contains("session.update")) {
                    webSocket.send("""{"type":"session.updated"}""")
                }
            }
        }))

        val connection = model().connect(connectRequest())
        // 配置确认前注入会话历史与音频
        connection.sendHistory(listOf(
            Content.fromText(Role.USER, "你好，之前聊到哪里了？"),
            Content.fromText(Role.MODEL, "上次聊到了实时语音会话。"),
        ))
        val pcm = ByteArray(16) { it.toByte() }
        connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=24000", data = pcm)))

        val pump = launch(Dispatchers.IO) { runCatching { connection.receive().collect {} } }
        try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) {
                    while (sentFrames.size < 4) delay(10)
                    true
                }
            }
            assertEquals(4, sentFrames.size)

            // 1. session.update
            val f0 = JsonParser.parseString(sentFrames[0]).asJsonObject
            assertEquals("session.update", f0.get("type").asString)

            // 2. conversation.item.create for user
            val f1 = JsonParser.parseString(sentFrames[1]).asJsonObject
            assertEquals("conversation.item.create", f1.get("type").asString)
            assertEquals("user", f1.getAsJsonObject("item").get("role").asString)
            assertEquals("input_text", f1.getAsJsonObject("item").getAsJsonArray("content")[0].asJsonObject.get("type").asString)
            assertEquals("你好，之前聊到哪里了？", f1.getAsJsonObject("item").getAsJsonArray("content")[0].asJsonObject.get("text").asString)

            // 3. conversation.item.create for assistant
            val f2 = JsonParser.parseString(sentFrames[2]).asJsonObject
            assertEquals("conversation.item.create", f2.get("type").asString)
            assertEquals("assistant", f2.getAsJsonObject("item").get("role").asString)
            assertEquals("output_text", f2.getAsJsonObject("item").getAsJsonArray("content")[0].asJsonObject.get("type").asString)
            assertEquals("上次聊到了实时语音会话。", f2.getAsJsonObject("item").getAsJsonArray("content")[0].asJsonObject.get("text").asString)

            // 4. input_audio_buffer.append
            val f3 = JsonParser.parseString(sentFrames[3]).asJsonObject
            assertEquals("input_audio_buffer.append", f3.get("type").asString)
        } finally {
            connection.closeSession()
            pump.cancel()
        }
    }

    @Test
    fun `sendHistory after sessionConfigured sends immediately`() = runTest {
        val sentFrames = java.util.concurrent.CopyOnWriteArrayList<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                sentFrames += text
                if (text.contains("session.update")) {
                    webSocket.send("""{"type":"session.updated"}""")
                }
            }
        }))

        val connection = model().connect(connectRequest())
        val pump = launch(Dispatchers.IO) { runCatching { connection.receive().collect {} } }
        try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) {
                    while (sentFrames.isEmpty()) delay(10)
                    true
                }
            }
            // session.update 已发出，再调用 sendHistory
            connection.sendHistory(listOf(
                Content.fromText(Role.USER, "追加历史"),
            ))
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5_000) {
                    while (sentFrames.size < 2) delay(10)
                    true
                }
            }
            assertEquals(2, sentFrames.size)
            val f1 = JsonParser.parseString(sentFrames[1]).asJsonObject
            assertEquals("conversation.item.create", f1.get("type").asString)
            assertEquals("user", f1.getAsJsonObject("item").get("role").asString)
            assertEquals("追加历史", f1.getAsJsonObject("item").getAsJsonArray("content")[0].asJsonObject.get("text").asString)
        } finally {
            connection.closeSession()
            pump.cancel()
        }
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
