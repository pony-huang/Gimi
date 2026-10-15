package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.ThinkingLevel
import com.google.adk.kt.types.Tool
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.mockk
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 验证应用工厂到官方后端的 HTTP 边界及主动接受的能力限制，不调用真实模型。 */
class ChatCompletionsIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(File("."))

    private lateinit var server: MockWebServer
    private val factory = AgentLLMModelFactory(
        mockk<AgentModelConfigurationSource>(),
        mockk<OfficialToolFunctionCatalog>(relaxed = true),
    )

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
    fun chatCompletionsModelUsesConfiguredEndpointKeyAndJsonOutput() = runBlocking {
        enqueueCompletion()

        val response = model().generateContent(
            request(config = GenerateContentConfig(responseMimeType = "application/json")),
            stream = false,
        ).toList().single()

        val captured = takeRequest()
        assertEquals("/custom/v1/chat/completions", captured.path)
        assertEquals("Bearer test-key", captured.getHeader("Authorization"))
        val body = JSONObject(captured.body.readUtf8())
        assertEquals("test-model", body.getString("model"))
        assertEquals("hello", body.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals("answer", response.content!!.parts.single().text)
        assertEquals(3, response.usageMetadata!!.totalTokenCount)
    }

    @Test
    fun thinkingConfigurationIsNotSent() = runBlocking {
        enqueueCompletion()

        model().generateContent(
            request(config = GenerateContentConfig(
                thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.HIGH),
            )),
            stream = false,
        ).toList()

        val body = takeBody()
        assertFalse(body.has("reasoning_effort"))
        assertFalse(body.has("thinking"))
        assertFalse(body.has("thinking_config"))
    }

    @Test
    fun structuredSchemaUsesOfficialStrictFormat() = runBlocking {
        enqueueCompletion()

        model().generateContent(
            request(config = GenerateContentConfig(
                responseMimeType = "application/json",
                responseSchema = Schema(
                    type = Type.OBJECT,
                    properties = mapOf("title" to Schema(type = Type.STRING)),
                    required = listOf("title"),
                ),
            )),
            stream = false,
        ).toList()

        val format = takeBody().getJSONObject("response_format")
        assertEquals("json_schema", format.getString("type"))
        assertTrue(format.getJSONObject("json_schema").getBoolean("strict"))
    }

    @Test
    fun localImageIsResolvedBeforeOfficialConversion() = runBlocking {
        enqueueCompletion()
        val image = temporaryFolder.newFile("photo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        model().generateContent(
            request(parts = listOf(Part(fileData = FileData(
                mimeType = "image/png",
                fileUri = image.toURI().toString(),
                displayName = image.name,
            )))),
            stream = false,
        ).toList()

        val content = takeBody().getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image_url", content.getJSONObject(0).getString("type"))
        assertEquals("data:image/png;base64,AQID", content.getJSONObject(0).getJSONObject("image_url").getString("url"))
    }

    @Test
    fun audioAndPdfInUserHistoryAreRejectedBeforeHttp() = runBlocking {
        for (mime in listOf("audio/wav", "application/pdf")) {
            val failure = runCatching {
                model().generateContent(
                    LlmRequest(contents = listOf(
                        Content(role = Role.USER, parts = listOf(Part(inlineData = Blob(
                            mimeType = mime,
                            data = byteArrayOf(1),
                        )))),
                        Content(role = Role.MODEL, parts = listOf(Part(text = "previous answer"))),
                        Content(role = Role.USER, parts = listOf(Part(text = "continue"))),
                    )),
                    stream = false,
                ).toList()
            }.exceptionOrNull()
            assertTrue("Expected unsupported media: $failure", failure is IllegalArgumentException)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun webSearchDeclarationUsesOrdinaryFunctionShape() = runBlocking {
        enqueueCompletion()

        model(serviceId = "openai").generateContent(
            request(config = GenerateContentConfig(tools = listOf(Tool(
                functionDeclarations = listOf(FunctionDeclaration(name = "web_search", description = "Search")),
            )))),
            stream = false,
        ).toList()

        val tool = takeBody().getJSONArray("tools").getJSONObject(0)
        assertEquals("function", tool.getString("type"))
        assertEquals("web_search", tool.getJSONObject("function").getString("name"))
    }

    @Test
    fun functionCallAndResultAreReplayedWithTheirId() = runBlocking {
        enqueueCompletion()

        model().generateContent(
            LlmRequest(contents = listOf(
                Content(role = Role.USER, parts = listOf(Part(text = "search"))),
                Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(
                    name = "lookup", id = "call-1", args = mapOf("q" to "weather"),
                )))),
                Content(role = Role.USER, parts = listOf(Part(functionResponse = FunctionResponse(
                    name = "lookup", id = "call-1", response = mapOf("result" to "sunny"),
                )))),
            )),
            stream = false,
        ).toList()

        val messages = takeBody().getJSONArray("messages")
        val call = messages.getJSONObject(1).getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("call-1", call.getString("id"))
        assertEquals("lookup", call.getJSONObject("function").getString("name"))
        val result = messages.getJSONObject(2)
        assertEquals("tool", result.getString("role"))
        assertEquals("call-1", result.getString("tool_call_id"))
        assertTrue(result.getString("content").contains("sunny"))
    }

    @Test
    fun streamingIgnoresThoughtExtensionsAndAggregatesTextAndUsage() = runBlocking {
        enqueueStream(
            """{"choices":[{"index":0,"delta":{"reasoning_content":"hidden","thinking":"hidden"}}]}""",
            """{"choices":[{"index":0,"delta":{"content":"hel"}}]}""",
            """{"choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":"stop"}]}""",
            """{"choices":[],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}""",
        )

        val responses = model().generateContent(request(), stream = true).toList()

        assertEquals(listOf("hel", "lo"), responses.filter { it.partial == true }
            .flatMap { it.content?.parts.orEmpty() }.mapNotNull { it.text })
        assertFalse(responses.flatMap { it.content?.parts.orEmpty() }.any { it.thought == true })
        assertEquals("hello", responses.last().content!!.parts.single().text)
        assertEquals(3, responses.last().usageMetadata!!.totalTokenCount)
        assertTrue(takeBody().getBoolean("stream"))
    }

    @Test
    fun streamingFunctionArgumentsAreAssembled() = runBlocking {
        enqueueStream(
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"lookup","arguments":"{\"q\":"}}]}}]}""",
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"weather\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )

        val responses = model().generateContent(request(), stream = true).toList()

        val call = responses.last().content!!.parts.single().functionCall!!
        assertEquals("call-1", call.id)
        assertEquals("lookup", call.name)
        assertEquals("weather", call.args["q"])
    }

    @Test
    fun httpFailurePropagatesToTheCaller() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"bad request"}}"""))

        val failure = runCatching { model().generateContent(request(), stream = false).toList() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun streamErrorPropagatesToTheCaller() = runBlocking {
        enqueueStream("""{"error":{"type":"invalid_request_error","message":"failed"}}""")

        val failure = runCatching { model().generateContent(request(), stream = true).toList() }.exceptionOrNull()

        assertNotNull(failure)
    }

    @Test
    fun cancellingPendingRequestCompletesPromptly() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.IO) { model().generateContent(request(), stream = true).toList() }
        try {
            withContext(Dispatchers.IO) { takeRequest() }
            withTimeout(5_000) { pending.cancelAndJoin() }
            assertTrue(pending.isCancelled)
        } finally {
            pending.cancelAndJoin()
        }
    }

    private fun model(serviceId: String = "compatible"): Model = factory.createModel(ModelConfig(
        serviceId = serviceId,
        baseType = ApiProtocol.OpenaiChatcompletions,
        modelId = "test-model",
        apiKey = "test-key",
        fullBaseUrl = server.url("/custom/v1/").toString(),
    ))

    private fun request(
        parts: List<Part> = listOf(Part(text = "hello")),
        config: GenerateContentConfig = GenerateContentConfig(),
    ) = LlmRequest(contents = listOf(Content(role = Role.USER, parts = parts)), config = config)

    private fun enqueueCompletion() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"id":"completion","object":"chat.completion","created":1,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"answer"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}""",
        ))
    }

    private fun enqueueStream(vararg chunks: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n",
        ))
    }

    private fun takeRequest() = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) {
        "No request reached the configured model endpoint"
    }

    private fun takeBody() = JSONObject(takeRequest().body.readUtf8())
}
