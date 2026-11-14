package github.ponyhuang.gimi.data.modelaccess.qwen.realtime

import com.google.gson.JsonParser
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qwen-Audio Realtime 协议边界：客户端事件的 JSON 形状与连接请求构造，
 * 以及服务端事件到内核事件的映射。与 OpenAI 协议同源不同形，
 * 断言锚定百炼文档的结构差异（pcm 字符串格式、双模态、审查头、静默窗口事件）。
 */
class QwenRealtimeProtocolTest {

    @Test
    fun `sessionUpdate carries dashscope session config`() {
        val payload = QwenRealtimeProtocol.sessionUpdate(instructions = "voice persona", voice = "longanqian_v3.1")
        assertEquals("session.update", payload.get("type").asString)

        val session = payload.getAsJsonObject("session")
        assertEquals("voice persona", session.get("instructions").asString)
        assertEquals("longanqian_v3.1", session.get("voice").asString)
        // 双模态：语音 + 文本（OpenAI 路径只出音频）。
        val modalities = session.getAsJsonArray("modalities")
        assertEquals("audio", modalities.get(0).asString)
        assertEquals("text", modalities.get(1).asString)
        // 格式用 "pcm" 字符串声明；采样率由协议固定（上行 16k / 下行 24k），不随会话配置。
        assertEquals("pcm", session.get("input_audio_format").asString)
        assertEquals("pcm", session.get("output_audio_format").asString)

        val vad = session.getAsJsonObject("turn_detection")
        assertEquals("server_vad", vad.get("type").asString)
        assertEquals(0.5, vad.get("threshold").asDouble, 0.0)
        assertEquals(QwenRealtimeProtocol.DEFAULT_SILENCE_DURATION_MS, vad.get("silence_duration_ms").asLong)
    }

    @Test
    fun `sessionUpdate carries nested function tools for the assistant mode`() {
        val payload = QwenRealtimeProtocol.sessionUpdate(
            instructions = "voice persona",
            voice = "longanqian_v3.1",
            tools = listOf(weatherDeclaration()),
        )
        // 官方文档的嵌套结构：声明对象包在 `function` 键下，type 在外层。
        val tools = payload.getAsJsonObject("session").getAsJsonArray("tools")
        assertEquals(1, tools.size())
        val tool = tools[0].asJsonObject
        assertEquals("function", tool.get("type").asString)
        val declaration = tool.getAsJsonObject("function")
        assertEquals("get_weather", declaration.get("name").asString)
        assertEquals("Query city weather", declaration.get("description").asString)
        val parameters = declaration.getAsJsonObject("parameters")
        assertEquals("object", parameters.get("type").asString)
        assertTrue(parameters.getAsJsonObject("properties").has("city"))

        // 实时轻聊不下发 tools 键。
        val light = QwenRealtimeProtocol.sessionUpdate(instructions = "voice persona", voice = "longanqian_v3.1")
        assertTrue(!light.getAsJsonObject("session").has("tools"))
    }

    @Test
    fun `parameterless tools still emit a valid object schema`() {
        // 无参数/空描述的工具不能产出 "{}" 或 "description": null；服务端按整批 tools
        // 校验，一条非法声明会让整批被丢弃，表现就是模型「没有工具」。
        val payload = QwenRealtimeProtocol.sessionUpdate(
            instructions = "voice persona",
            voice = "longanqian_v3.1",
            tools = listOf(
                com.google.adk.kt.types.FunctionDeclaration(name = "get_time", description = ""),
            ),
        )
        val function = payload.getAsJsonObject("session")
            .getAsJsonArray("tools")[0].asJsonObject
            .getAsJsonObject("function")
        val parameters = function.getAsJsonObject("parameters")
        assertEquals("object", parameters.get("type").asString)
        assertTrue(parameters.has("properties"))
        assertEquals("Call get_time.", function.get("description").asString)
    }

    @Test
    fun `functionCallOutput and createResponse carry the write back contract`() {
        val output = QwenRealtimeProtocol.functionCallOutput("c1", "{\"temperature\":18}")
        assertEquals("conversation.item.create", output.get("type").asString)
        val item = output.getAsJsonObject("item")
        assertEquals("function_call_output", item.get("type").asString)
        assertEquals("c1", item.get("call_id").asString)
        assertEquals("{\"temperature\":18}", item.get("output").asString)

        val trigger = QwenRealtimeProtocol.createResponse()
        assertEquals("response.create", trigger.get("type").asString)
    }

    @Test
    fun `appendAudio encodes 16k pcm as base64`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val payload = QwenRealtimeProtocol.appendAudio(pcm)
        assertEquals("input_audio_buffer.append", payload.get("type").asString)
        val decoded = java.util.Base64.getDecoder().decode(payload.get("audio").asString)
        assertTrue(pcm.contentEquals(decoded))
    }

    @Test
    fun `connectRequest maps wss to https appends model query and dataInspection header`() {
        val request: Request = QwenRealtimeProtocol.connectRequest(
            "wss://dashscope.aliyuncs.com/api-ws/v1/realtime", "qwen-audio-3.1-realtime-plus", apiKey = "sk-test",
        )
        assertEquals(
            "https://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen-audio-3.1-realtime-plus",
            request.url.toString(),
        )
        assertEquals("Bearer sk-test", request.headers.get("Authorization"))
        // 内容审查开关恒定下发，跟随官方示例默认行为。
        assertEquals("disable", request.headers.get("x-dashscope-dataInspection"))
    }

    @Test
    fun `connectRequest accepts workspace variant endpoint`() {
        val request: Request = QwenRealtimeProtocol.connectRequest(
            "wss://ws-123.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime", "qwen-audio-3.0-realtime-flash", apiKey = "k",
        )
        assertEquals("https://ws-123.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen-audio-3.0-realtime-flash", request.url.toString())
    }

    @Test
    fun `connectRequest omits workspace header when workspace id is blank`() {
        fun request(workspaceId: String?): Request = QwenRealtimeProtocol.connectRequest(
            "wss://dashscope.aliyuncs.com/api-ws/v1/realtime", "m", apiKey = "k", workspaceId = workspaceId,
        )
        // null、空白都视为未填写业务空间，不下发业务空间请求头。
        assertTrue(request(null).headers.get("X-DashScope-WorkSpace") == null)
        assertTrue(request("  ").headers.get("X-DashScope-WorkSpace") == null)
    }

    @Test
    fun `connectRequest carries workspace header when workspace id is set`() {
        val request: Request = QwenRealtimeProtocol.connectRequest(
            "wss://ws-x.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime", "m", apiKey = "k", workspaceId = " ws-x ",
        )
        // 业务空间 ID 与专属域名指名同一空间；头与域名一同下发，与官方 SDK 行为一致。
        assertEquals("ws-x", request.headers.get("X-DashScope-WorkSpace"))
    }

    @Test
    fun `parse maps server events to kernel events`() {
        fun event(json: String) = QwenRealtimeProtocol.parse(JsonParser.parseString(json).asJsonObject)

        assertTrue(event("""{"type":"session.created"}""") is QwenRealtimeEvent.Ignored)
        assertTrue(event("""{"type":"session.updated"}""") is QwenRealtimeEvent.SessionUpdated)
        assertTrue(event("""{"type":"input_audio_buffer.speech_started"}""") is QwenRealtimeEvent.SpeechStarted)

        val committed = event("""{"type":"input_audio_buffer.committed","item_id":"u1"}""")
        assertEquals("u1", (committed as QwenRealtimeEvent.TurnCommitted).itemId)

        // 用户字幕增量：text 为已确定累计文本，stash 暂存文本不进字幕。
        val delta = event(
            """{"type":"conversation.item.input_audio_transcription.delta","text":"你好","stash":"世"}""",
        )
        assertEquals("你好", (delta as QwenRealtimeEvent.UserTranscriptDelta).text)

        val transcript = event(
            """{"type":"conversation.item.input_audio_transcription.completed","transcript":"你好世界"}""",
        )
        assertEquals("你好世界", (transcript as QwenRealtimeEvent.UserTranscript).transcript)

        assertTrue(event("""{"type":"response.created"}""") is QwenRealtimeEvent.ResponseCreated)

        val audio = event("""{"type":"response.audio.delta","delta":"AQID"}""")
        assertTrue(byteArrayOf(1, 2, 3).contentEquals((audio as QwenRealtimeEvent.AudioDelta).pcm24k))

        val assistantDelta = event("""{"type":"response.audio_transcript.delta","delta":"哈"}""")
        assertEquals("哈", (assistantDelta as QwenRealtimeEvent.AssistantTranscriptDelta).text)

        val assistantDone = event("""{"type":"response.audio_transcript.done","transcript":"哈哈"}""")
        assertEquals("哈哈", (assistantDone as QwenRealtimeEvent.AssistantTranscriptDone).transcript)

        val toolCallItem = event(
            """{"type":"response.output_item.added","item":{"type":"function_call","call_id":"c1","name":"get_weather"}}""",
        )
        assertEquals("c1", (toolCallItem as QwenRealtimeEvent.ToolCallStarted).callId)
        assertEquals("get_weather", toolCallItem.name)

        val toolArgsDelta = event(
            """{"type":"response.function_call_arguments.delta","call_id":"c1","delta":"{\"city\":"}""",
        )
        assertEquals("c1", (toolArgsDelta as QwenRealtimeEvent.ToolCallArgumentsDelta).callId)
        assertEquals("{\"city\":", toolArgsDelta.delta)

        val toolCall = event(
            """{"type":"response.function_call_arguments.done","call_id":"c1","name":"get_weather","arguments":"{\"city\":\"杭州\"}"}""",
        )
        assertEquals("c1", (toolCall as QwenRealtimeEvent.ToolCallArgumentsDone).callId)
        assertEquals("get_weather", toolCall.name)
        assertEquals("{\"city\":\"杭州\"}", toolCall.arguments)

        val done = event("""{"type":"response.done","response":{"status":"cancelled"}}""")
        assertEquals("cancelled", (done as QwenRealtimeEvent.ResponseDone).status)

        val error = event("""{"type":"error","error":{"code":"bad","message":"boom"}}""")
        assertEquals("boom", (error as QwenRealtimeEvent.ProtocolError).message)
        assertEquals("bad", error.code)

        // smart_turn 专属与未知事件向前兼容忽略。
        assertTrue(event("""{"type":"conversation.item.ambient_audio_transcription.delta"}""") is QwenRealtimeEvent.Ignored)
        assertTrue(event("""{"type":"voiceprint_audio_list.completed"}""") is QwenRealtimeEvent.Ignored)
        assertTrue(event("""{"type":"something.unknown"}""") is QwenRealtimeEvent.Ignored)
    }

    /** 测试工具声明：参数结构与模型测试共用一份形状。 */
    private fun weatherDeclaration() = com.google.adk.kt.types.FunctionDeclaration(
        name = "get_weather",
        description = "Query city weather",
        parameters = com.google.adk.kt.types.Schema(
            type = com.google.adk.kt.types.Type.OBJECT,
            properties = mapOf("city" to com.google.adk.kt.types.Schema(type = com.google.adk.kt.types.Type.STRING)),
            required = listOf("city"),
        ),
    )
}
