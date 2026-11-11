package github.ponyhuang.gimi.data.agent.voice

import com.google.gson.JsonParser
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenAI Realtime 协议边界：客户端事件的 JSON 形状与连接请求构造，
 * 以及服务端事件到内核事件的映射。上行计费依赖这些帧的准确性。
 */
class OpenAiRealtimeProtocolTest {

    @Test
    fun `sessionUpdate carries realtime session config`() {
        val payload = OpenAiRealtimeProtocol.sessionUpdate(instructions = "voice persona", voice = "cedar")
        assertEquals("session.update", payload.get("type").asString)

        val session = payload.getAsJsonObject("session")
        assertEquals("realtime", session.get("type").asString)
        assertEquals("voice persona", session.get("instructions").asString)
        assertEquals("cedar", session.get("voice").asString)
        assertEquals("audio", session.getAsJsonArray("output_modalities").get(0).asString)

        val input = session.getAsJsonObject("audio").getAsJsonObject("input")
        assertEquals("audio/pcm", input.getAsJsonObject("format").get("type").asString)
        assertEquals(24_000, input.getAsJsonObject("format").get("rate").asInt)
        assertEquals("gpt-4o-transcribe", input.getAsJsonObject("transcription").get("model").asString)

        val vad = input.getAsJsonObject("turn_detection")
        assertEquals("server_vad", vad.get("type").asString)
        assertEquals(OpenAiRealtimeProtocol.DEFAULT_SILENCE_DURATION_MS, vad.get("silence_duration_ms").asLong)
        assertEquals(true, vad.get("create_response").asBoolean)
        assertEquals(true, vad.get("interrupt_response").asBoolean)

        val output = session.getAsJsonObject("audio").getAsJsonObject("output")
        assertEquals(24_000, output.getAsJsonObject("format").get("rate").asInt)
    }

    @Test
    fun `sessionUpdate carries flat function tools for the assistant mode`() {
        val payload = OpenAiRealtimeProtocol.sessionUpdate(
            instructions = "voice persona",
            voice = "cedar",
            tools = listOf(weatherDeclaration()),
        )
        // GA 的 function 工具是扁平结构：type/name/description/parameters 顶层并列。
        val tools = payload.getAsJsonObject("session").getAsJsonArray("tools")
        assertEquals(1, tools.size())
        val tool = tools[0].asJsonObject
        assertEquals("function", tool.get("type").asString)
        assertEquals("get_weather", tool.get("name").asString)
        assertEquals("Query city weather", tool.get("description").asString)
        val parameters = tool.getAsJsonObject("parameters")
        assertEquals("object", parameters.get("type").asString)
        assertTrue(parameters.getAsJsonObject("properties").has("city"))

        // 实时轻聊不下发 tools 键。
        val light = OpenAiRealtimeProtocol.sessionUpdate(instructions = "voice persona", voice = "cedar")
        assertTrue(!light.getAsJsonObject("session").has("tools"))
    }

    @Test
    fun `functionCallOutput and createResponse carry the write back contract`() {
        val output = OpenAiRealtimeProtocol.functionCallOutput("c1", "{\"temperature\":18}")
        assertEquals("conversation.item.create", output.get("type").asString)
        val item = output.getAsJsonObject("item")
        assertEquals("function_call_output", item.get("type").asString)
        assertEquals("c1", item.get("call_id").asString)
        assertEquals("{\"temperature\":18}", item.get("output").asString)

        val trigger = OpenAiRealtimeProtocol.createResponse()
        assertEquals("response.create", trigger.get("type").asString)
    }

    @Test
    fun `appendAudio encodes pcm as base64`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val payload = OpenAiRealtimeProtocol.appendAudio(pcm)
        assertEquals("input_audio_buffer.append", payload.get("type").asString)
        val decoded = java.util.Base64.getDecoder().decode(payload.get("audio").asString)
        assertTrue(pcm.contentEquals(decoded))
    }

    @Test
    fun `truncate targets assistant item at content zero`() {
        val payload = OpenAiRealtimeProtocol.truncate(assistantItemId = "item-1", audioEndMs = 1500L)
        assertEquals("conversation.item.truncate", payload.get("type").asString)
        assertEquals("item-1", payload.get("item_id").asString)
        assertEquals(0, payload.get("content_index").asInt)
        assertEquals(1500L, payload.get("audio_end_ms").asLong)
    }

    @Test
    fun `connectRequest maps wss to https and appends model query`() {
        val request: Request = OpenAiRealtimeProtocol.connectRequest(
            "wss://api.openai.com/v1/realtime", "gpt-realtime-2.1", apiKey = "sk-test",
        )
        assertEquals("https://api.openai.com/v1/realtime?model=gpt-realtime-2.1", request.url.toString())
        assertEquals("Bearer sk-test", request.headers.get("Authorization"))
        assertNull(request.headers.get("OpenAI-Beta"))
    }

    @Test
    fun `parse maps server events to kernel events`() {
        fun event(json: String) = OpenAiRealtimeProtocol.parse(JsonParser.parseString(json).asJsonObject)

        assertTrue(event("""{"type":"session.updated"}""") is OpenAiRealtimeEvent.SessionUpdated)
        assertTrue(event("""{"type":"input_audio_buffer.speech_started"}""") is OpenAiRealtimeEvent.SpeechStarted)

        val committed = event("""{"type":"input_audio_buffer.committed","item_id":"u1"}""")
        assertEquals("u1", (committed as OpenAiRealtimeEvent.TurnCommitted).itemId)

        val transcript = event(
            """{"type":"conversation.item.input_audio_transcription.completed","transcript":"你好"}""",
        )
        assertEquals("你好", (transcript as OpenAiRealtimeEvent.UserTranscript).transcript)

        val audio = event("""{"type":"response.output_audio.delta","delta":"AQID"}""")
        assertTrue(byteArrayOf(1, 2, 3).contentEquals((audio as OpenAiRealtimeEvent.AudioDelta).pcm24k))

        val delta = event("""{"type":"response.output_audio_transcript.delta","delta":"哈"}""")
        assertEquals("哈", (delta as OpenAiRealtimeEvent.AssistantTranscriptDelta).text)

        val done = event("""{"type":"response.done","response":{"status":"completed"}}""")
        assertEquals("completed", (done as OpenAiRealtimeEvent.ResponseDone).status)

        val toolCall = event(
            """{"type":"response.output_item.added","item":{"type":"function_call","call_id":"c1","name":"get_weather"}}""",
        )
        assertEquals("c1", (toolCall as OpenAiRealtimeEvent.ToolCallStarted).callId)
        assertEquals("get_weather", toolCall.name)

        val toolArgsDelta = event(
            """{"type":"response.function_call_arguments.delta","call_id":"c1","delta":"{\"city\":"}""",
        )
        assertEquals("c1", (toolArgsDelta as OpenAiRealtimeEvent.ToolCallArgumentsDelta).callId)
        assertEquals("{\"city\":", toolArgsDelta.delta)

        val toolArgsDone = event(
            """{"type":"response.function_call_arguments.done","call_id":"c1","name":"get_weather","arguments":"{\"city\":\"杭州\"}"}""",
        )
        assertEquals("c1", (toolArgsDone as OpenAiRealtimeEvent.ToolCallArgumentsDone).callId)
        assertEquals("get_weather", toolArgsDone.name)
        assertEquals("{\"city\":\"杭州\"}", toolArgsDone.arguments)

        val assistantItem = event(
            """{"type":"response.output_item.added","item":{"type":"message","role":"assistant","id":"a1"}}""",
        )
        assertEquals("a1", (assistantItem as OpenAiRealtimeEvent.AssistantItemStarted).itemId)

        val error = event("""{"type":"error","error":{"code":"bad","message":"boom"}}""")
        assertEquals("boom", (error as OpenAiRealtimeEvent.ProtocolError).message)
        assertEquals("bad", error.code)

        assertTrue(event("""{"type":"rate_limits.updated"}""") is OpenAiRealtimeEvent.Ignored)
        assertTrue(event("""{"type":"something.unknown"}""") is OpenAiRealtimeEvent.Ignored)
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
