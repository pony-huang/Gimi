package github.ponyhuang.gimi.data.agent.model.files

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.data.agent.ModelConfig
import github.ponyhuang.gimi.domain.conversation.cloud.*
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 工厂到真实 SDK HTTP 的文件边界测试，不连接厂商；覆盖历史、工具和流式输出保真。 */
class FileChatCompletionsIntegrationTest {
    private val server = MockWebServer()
    private val source = mockk<AgentModelConfigurationSource>()
    private val repository = mockk<CloudFileRepository>()
    private val support = mockk<CloudFileChatSupport>()
    private lateinit var service: LLMModelSetting
    private lateinit var config: ModelConfig
    private var file = CloudFile("file-1", "scope", "report.pdf", "application/pdf", 100,
        "hash", 1, Long.MAX_VALUE, null, "/shared/original.pdf")

    @Before fun setUp() {
        server.start()
        service = LLMModelSetting("custom", "Custom", true, apiKey = "fake-key", apiBaseUrl = server.url("/v1").toString(),
            apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = "", groups = emptyList(), isFileUploadEnabled = true)
        config = ModelConfig(service.id, service.apiProtocol, "custom-model", service.apiKey, service.openAiCompatibleBaseUrl)
        every { source.currentServices() } answers { listOf(service) }
        every { support.supports(any(), any(), any()) } returns true
        coEvery { repository.list(any()) } answers { listOf(file) }
        coEvery { repository.prepareExisting(any(), any()) } answers { flowOf(CloudFilePreparation.Ready(file)) }
    }
    @After fun tearDown() { server.shutdown() }

    private fun attachment() = FileAttachment(file.mimeType, "local-id", file.sizeBytes, file.displayName,
        payloadReference = file.localReference, cloudReference = CloudAttachmentReference(file.id, file.scope))
    private fun part() = Part(fileData = FileData(fileUri = CloudFileAttachmentCodec.encode(attachment()), mimeType = file.mimeType, displayName = file.displayName))
    private fun model() = AgentLLMModelFactory(source, repository, support).createModel(config)
    private fun request(config: GenerateContentConfig = GenerateContentConfig()) = LlmRequest(
        contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = "summarize"), part()))), config = config)
    private fun completion(content: String = "answer") = MockResponse().setHeader("Content-Type", "application/json").setBody(
        """{"id":"response","object":"chat.completion","created":1,"model":"custom-model","choices":[{"index":0,"message":{"role":"assistant","content":"$content"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}""")
    private fun body(): JSONObject {
        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer fake-key", request.getHeader("Authorization"))
        return JSONObject(request.body.readUtf8())
    }

    @Test fun fileIdIsSentWithoutLocalPathOrAppEnvelopeAndUnaryUsageSurvives() = runBlocking {
        server.enqueue(completion())
        val result = model().generateContent(request(), false).toList().single()
        val json = body()
        assertEquals("file-1", json.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1).getJSONObject("file").getString("file_id"))
        assertFalse(json.toString().contains("gimi-cloud-file"))
        assertFalse(json.toString().contains("/shared/"))
        assertFalse(json.toString().contains("scope"))
        assertEquals("answer", result.content!!.parts.single().text)
        assertEquals(3, result.usageMetadata!!.totalTokenCount)
    }

    @Test fun structuredOutputToolDefinitionsAndHistoricalResultsArePreserved() = runBlocking {
        server.enqueue(completion())
        val cfg = GenerateContentConfig(systemInstruction = Content(parts = listOf(Part(text = "be accurate"))),
            responseSchema = Schema(type = Type.OBJECT, properties = mapOf("summary" to Schema(type = Type.STRING))),
            tools = listOf(Tool(functionDeclarations = listOf(FunctionDeclaration(name = "lookup", description = "Look up a report", parameters = Schema(type = Type.OBJECT))))))
        val original = request(cfg)
        val withHistory = original.copy(contents = original.contents + listOf(
            Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(name = "lookup", args = mapOf("query" to "report"), id = "call-1")))),
            Content(role = Role.USER, parts = listOf(Part(functionResponse = FunctionResponse(name = "lookup", id = "call-1", response = mapOf("result" to "found")))))))
        model().generateContent(withHistory, false).toList()
        val json = body()
        assertEquals("json_schema", json.getJSONObject("response_format").getString("type"))
        assertTrue(json.getJSONObject("response_format").getJSONObject("json_schema").getBoolean("strict"))
        assertEquals("lookup", json.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
        val messages = json.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("call-1", messages.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("tool", messages.getJSONObject(3).getString("role"))
        assertEquals("call-1", messages.getJSONObject(3).getString("tool_call_id"))
        assertEquals("found", messages.getJSONObject(3).getString("content"))
    }

    @Test fun expiredHistoricalIdIsRefreshedOnlyInRequestCopy() = runBlocking {
        val original = request()
        file = file.copy(expiresAtMillis = 1)
        coEvery { repository.prepareExisting(any(), "file-1") } returns flowOf(CloudFilePreparation.Ready(file.copy(id = "file-refreshed", expiresAtMillis = Long.MAX_VALUE)))
        server.enqueue(completion())
        model().generateContent(original, false).toList()
        val json = body()
        assertEquals("file-refreshed", json.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1).getJSONObject("file").getString("file_id"))
        val saved = original.contents[0].parts[1].fileData!!
        assertEquals("file-1", CloudFileAttachmentCodec.decode(saved.fileUri!!, saved.mimeType!!, saved.displayName!!).cloudReference!!.fileId)
    }

    @Test fun changedCredentialsUnsupportedProtocolAndScopeMismatchNeverReachNetwork() = runBlocking {
        val original = request()
        for (change in listOf("key", "protocol", "scope", "disabled", "unsupported")) {
            val oldService = service
            val oldFile = file
            when (change) {
                "key" -> service = service.copy(apiKey = "rotated")
                "protocol" -> service = service.copy(apiProtocol = ApiProtocol.Anthropic)
                "scope" -> file = file.copy(scope = "other-scope")
                "disabled" -> service = service.copy(isFileUploadEnabled = false)
                "unsupported" -> every { support.supports(any(), any(), any()) } returns false
            }
            val failure = runCatching { model().generateContent(original, false).toList() }.exceptionOrNull()
            assertTrue(change, failure is CloudFileException)
            service = oldService
            file = oldFile
        }
        assertEquals(0, server.requestCount)
        coVerify(exactly = 0) { repository.prepareExisting(any(), any()) }
    }

    @Test fun unreadableCloudReferencesAndFailedPreparationCannotBecomeTextOrLocalFiles() = runBlocking {
        val malformed = request().copy(contents = listOf(Content(role = Role.USER, parts = listOf(
            Part(fileData = FileData(fileUri = "gimi-cloud-file:v9:invalid", mimeType = "application/pdf")),
        ))))
        assertTrue(runCatching { model().generateContent(malformed, false).toList() }.exceptionOrNull() is CloudFileException)
        coEvery { repository.prepareExisting(any(), any()) } returns flowOf(CloudFilePreparation.Failed(CloudFileFailure.LocalFileUnavailable))
        val failure = runCatching { model().generateContent(request(), false).toList() }.exceptionOrNull() as CloudFileException
        assertEquals(CloudFileFailure.LocalFileUnavailable, failure.reason)
        assertEquals(0, server.requestCount)
    }

    @Test fun totalHistoryFileSizeIsCheckedBeforeRefreshingOrCallingModel() = runBlocking {
        file = file.copy(sizeBytes = CloudFileRequestLimits.MAX_BYTES)
        val original = request().copy(contents = listOf(Content(role = Role.USER, parts = listOf(part(), part()))))
        val failure = runCatching { model().generateContent(original, false).toList() }.exceptionOrNull() as CloudFileException
        assertEquals(CloudFileFailure.FileTooLarge, failure.reason)
        coVerify(exactly = 0) { repository.prepareExisting(any(), any()) }
        assertEquals(0, server.requestCount)
    }

    @Test fun streamingTextToolArgumentsAndUsageAreAggregatedWithoutLosingFragments() = runBlocking {
        val chunks = listOf(
            """"choices":[{"index":0,"delta":{"content":"summary "}}]""",
            """"choices":[{"index":0,"delta":{"content":"done","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"lookup","arguments":"{\"q\":"}}]}}]""",
            """"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"report\"}"}}]},"finish_reason":"tool_calls"}]""",
            """"choices":[],"usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":5}""",
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(chunks.joinToString("") {
            "data: {\"id\":\"response\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"custom-model\",$it}\n\n"
        } + "data: [DONE]\n\n"))
        val result = model().generateContent(request(), true).toList()
        val json = body()
        assertTrue(json.getBoolean("stream"))
        assertTrue(json.getJSONObject("stream_options").getBoolean("include_usage"))
        val final = result.last()
        assertFalse(final.partial)
        assertEquals(5, final.usageMetadata!!.totalTokenCount)
        assertEquals("summary done", final.content!!.parts.mapNotNull { it.text }.joinToString(""))
        assertEquals("report", final.content!!.parts.first { it.functionCall != null }.functionCall!!.args["q"])
        assertEquals("call-1", final.content!!.parts.first { it.functionCall != null }.functionCall!!.id)
    }

    @Test fun rejectionDoesNotRetryOrExposeProviderResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
            .setBody("""{"error":{"message":"private-document-text","type":"invalid_request_error"}}"""))
        val error = runCatching { model().generateContent(request(), false).toList() }.exceptionOrNull()!!
        assertTrue(error.toString().contains("HTTP 400"))
        assertTrue(generateSequence(error) { it.cause }.none { it.toString().contains("private-document-text") })
        body()
        assertEquals(null, server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test fun cancellingWaitingStreamClosesSdkWithoutRetry() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(" ").setBodyDelay(30, TimeUnit.SECONDS))
        val job = async(Dispatchers.IO) { model().generateContent(request(), true).toList() }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        withTimeout(5_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertEquals(1, server.requestCount)
    }
}
