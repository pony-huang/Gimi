package github.ponyhuang.gimi.data.modelaccess.anthropic.llm

import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Tool
import github.ponyhuang.gimi.data.modelaccess.deepseek.llm.DeepSeekMessageRequestConverter
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.domain.conversation.model.WebCitationMetadata
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.modelaccess.deepseek.llm.DeepSeekMessageChat
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 从真实 SDK 的 SSE 入口验证思考、签名、工具名与取消，不依赖适配器内部方法。 */
class ClaudeStreamingIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AnthropicClient
    private lateinit var model: Claude

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl(server.url("/").toString())
            .maxRetries(0)
            .build()
        model = Claude("claude-sonnet-4-6", client)
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun webCitationsFollowStreamingTextAndSurviveTheAggregate() = runBlocking {
        enqueueStream(
            start(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"中🙂"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"citations_delta","citation":{"type":"web_search_result_location","url":"https://example.org/story","title":"Story","encrypted_index":"index","cited_text":"Source"}}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"文"}}""",
            """{"type":"content_block_stop","index":0}""",
            finish(),
            """{"type":"message_stop"}""",
        )
        val responses = Claude("claude-sonnet-4-6", client, webSearchReplayScope = "scope")
            .generateContent(request(), stream = true).toList()
        val live = responses.filter { it.partial == true }.flatMap {
            WebCitationMetadata.decode(it.customMetadata?.get(WebCitationMetadata.KEY))
        }.single()
        assertEquals(3, live.endOffset)
        assertEquals("https://example.org/story", live.source.url)
        assertEquals(listOf(live), WebCitationMetadata.decode(responses.last().customMetadata?.get(WebCitationMetadata.KEY)))
    }

    @Test
    fun deepSeekNativeSearchReturnsSiteIconAndReplaysOnlyOnTheSameConnection() = runBlocking {
        val scope = "deepseek-connection"
        val converter = DeepSeekMessageRequestConverter("deepseek-flash", 8196, webSearchReplayScope = scope)
        val adapter = DeepSeekMessageChat("deepseek-flash", client, webSearchReplayScope = scope, requestConverter = converter::convert)
        val original = request()
        val withSearch = original.copy(config = original.config.copy(tools = listOf(Tool(
            functionDeclarations = listOf(FunctionDeclaration(name = OfficialToolIds.DEEPSEEK_NATIVE_WEB_SEARCH, description = "Search the web")),
        ))))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"id":"msg_web","type":"message","role":"assistant","model":"deepseek-flash","stop_reason":"end_turn","content":[
                {"type":"server_tool_use","id":"srvtoolu_search","name":"deepseek_web_search","input":{"query":"fixtures"}},
                {"type":"web_search_tool_result","tool_use_id":"srvtoolu_search","kind":"search","signature":"signed","content":[
                    {"type":"web_search_result","url":"https://example.org/story","title":"Story","encrypted_content":"opaque","site_icon":"https://example.org/icon.png"}
                ]},
                {"type":"text","text":"Reply","citations":[{"type":"web_search_result_location","url":"https://example.org/story","title":"Story","encrypted_index":"","cited_text":"Reply"}]}
            ],"usage":{"input_tokens":10,"output_tokens":2}}""",
        ))
        val reply = adapter.generateContent(withSearch, stream = false).toList().single()
        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("deepseek_web_search", sent.getJSONArray("tools").getJSONObject(0).getString("type"))
        val citation = WebCitationMetadata.decode(reply.customMetadata?.get(WebCitationMetadata.KEY)).single()
        assertEquals("https://example.org/icon.png", citation.source.iconUrl)
        val next = original.copy(contents = original.contents + requireNotNull(reply.content) + original.contents)
        fun blocks(params: com.anthropic.models.messages.MessageCreateParams) =
            com.anthropic.core.jsonMapper().writeValueAsString(params.messages()[1])
        assertTrue(blocks(converter.convert(next)).contains("signed"))
        val changed = DeepSeekMessageRequestConverter("deepseek-flash", 8196, webSearchReplayScope = "another")
        assertFalse(blocks(changed.convert(next)).contains("signed"))
        assertTrue(blocks(changed.convert(next)).contains("Reply"))
    }

    @Test
    fun hostedToolStreamPreservesInputsFromTheStartBlockAndFromDeltas() = runBlocking {
        val scope = "deepseek-connection"
        val converter = DeepSeekMessageRequestConverter("deepseek-flash", 8196, webSearchReplayScope = scope)
        val adapter = DeepSeekMessageChat(
            "deepseek-flash", client, webSearchReplayScope = scope, requestConverter = converter::convert,
        )
        enqueueStream(
            start(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"server_tool_use","id":"srvtoolu_search","name":"deepseek_web_search","input":{"query":"input from start"}}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"server_tool_use","id":"srvtoolu_second","name":"deepseek_web_search","input":{}}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"query\":"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"input from delta\"}"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"content_block_start","index":2,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"Reply"}}""",
            """{"type":"content_block_delta","index":2,"delta":{"type":"citations_delta","citation":{"type":"web_search_result_location","url":"https://example.org/story","title":"Story","encrypted_index":"","cited_text":"Reply"}}}""",
            """{"type":"content_block_stop","index":2}""",
            finish(),
            """{"type":"message_stop"}""",
        )

        val original = request()
        val reply = adapter.generateContent(original, stream = true).toList().last()
        assertEquals(reply.errorMessage, "Reply", reply.content?.parts?.single()?.text)
        val citation = WebCitationMetadata.decode(reply.customMetadata?.get(WebCitationMetadata.KEY)).single()
        assertEquals("https://example.org/story", citation.source.url)
        val next = original.copy(contents = original.contents + requireNotNull(reply.content) + original.contents)
        val replay = JSONObject(com.anthropic.core.jsonMapper().writeValueAsString(converter.convert(next).messages()[1]))
            .getJSONArray("content")
        assertEquals("input from start", replay.getJSONObject(0).getJSONObject("input").getString("query"))
        assertEquals("input from delta", replay.getJSONObject(1).getJSONObject("input").getString("query"))
    }

    @Test
    fun uploadedFilesIncludeRequiredBetaHeaderForBothTransports() = runBlocking {
        for (adapter in listOf(model, DeepSeekMessageChat("deepseek-flash", client))) {
            for (stream in listOf(false, true)) {
                if (stream) enqueueStream(start(), finish(), """{"type":"message_stop"}""")
                else enqueueMessage()
                adapter.generateContent(LlmRequest(contents = listOf(Content(
                    role = Role.USER,
                    parts = listOf(Part(fileData = FileData(
                        mimeType = "application/pdf", fileUri = "gimi-file://anthropic/file_01",
                    ))),
                ))), stream = stream).toList()
                assertEquals("files-api-2025-04-14", server.takeRequest().getHeader("anthropic-beta"))
            }
        }
    }

    @Test
    fun streamsThoughtAndTextThenReturnsSignedBlocksInOrder() = runBlocking {
        enqueueStream(
            start(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"分析"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"中"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"signed-thought"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"答案"}}""",
            """{"type":"content_block_stop","index":1}""",
            finish(),
            """{"type":"message_stop"}""",
        )

        val responses = model.generateContent(request(), stream = true).toList()

        val deltas = responses.filter { it.partial == true }.flatMap { it.content!!.parts }
        assertEquals(listOf("分析", "中", "答案"), deltas.map { it.text })
        assertEquals(listOf(true, true, false), deltas.map { it.thought == true })
        val final = responses.last()
        assertFalse(final.partial == true)
        assertEquals("claude-sonnet-4-6", final.modelVersion)
        val blocks = final.content!!.parts
        assertEquals(listOf("分析中", "答案"), blocks.map { it.text })
        assertArrayEquals("signed-thought".toByteArray(), blocks[0].thoughtSignature)
        assertEquals(3, final.usageMetadata!!.totalTokenCount)

        server.takeRequest()
        enqueueMessage()
        model.generateContent(LlmRequest(contents = listOf(
            Content(role = Role.MODEL, parts = blocks),
            Content(role = Role.USER, parts = listOf(Part(text = "继续"))),
        )), stream = false).toList()
        val replay = JSONObject(server.takeRequest().body.readUtf8())
            .getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("thinking", replay.getString("type"))
        assertEquals("分析中", replay.getString("thinking"))
        assertEquals("signed-thought", replay.getString("signature"))
    }

    @Test
    fun arbitraryToolNameAndEmptyArgumentsArePreserved() = runBlocking {
        enqueueStream(
            start(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tool-1","name":"custom_clock_42","input":{}}}""",
            """{"type":"content_block_stop","index":0}""",
            finish("tool_use"),
            """{"type":"message_stop"}""",
        )

        val response = model.generateContent(request(), stream = true).toList().last()

        val call = response.content!!.parts.single().functionCall!!
        assertEquals("custom_clock_42", call.name)
        assertEquals("tool-1", call.id)
        assertTrue(call.args.isEmpty())
    }

    @Test
    fun streamingToolArgumentFragmentsAreAssembled() = runBlocking {
        enqueueStream(
            start(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tool-1","name":"my_lookup","input":{}}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"query\":"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"\"hello\"}"}}""",
            """{"type":"content_block_stop","index":0}""",
            finish("tool_use"),
            """{"type":"message_stop"}""",
        )

        val response = model.generateContent(request(), stream = true).toList().last()

        val call = response.content!!.parts.single().functionCall!!
        assertEquals("my_lookup", call.name)
        assertEquals("hello", call.args["query"])
    }

    @Test
    fun redactedThoughtIsRetainedAndReplayedWithoutInventedText() = runBlocking {
        enqueueStream(
            start(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"redacted_thinking","data":"encrypted-thought"}}""",
            """{"type":"content_block_stop","index":0}""",
            finish(),
            """{"type":"message_stop"}""",
        )
        val parts = model.generateContent(request(), stream = true).toList().last().content!!.parts
        assertEquals(null, parts.single().text)
        assertTrue(parts.single().thought == true)
        assertArrayEquals("encrypted-thought".toByteArray(), parts.single().thoughtSignature)

        server.takeRequest()
        enqueueMessage()
        model.generateContent(LlmRequest(contents = listOf(
            Content(role = Role.MODEL, parts = parts),
            Content(role = Role.USER, parts = listOf(Part(text = "continue"))),
        )), stream = false).toList()

        val thought = JSONObject(server.takeRequest().body.readUtf8())
            .getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("redacted_thinking", thought.getString("type"))
        assertEquals("encrypted-thought", thought.getString("data"))
        assertFalse(thought.has("thinking"))
    }

    @Test
    fun unsignedThoughtsAreNotSentBackAsInvalidThinkingBlocks() = runBlocking {
        enqueueMessage()

        model.generateContent(LlmRequest(contents = listOf(Content(
            role = Role.MODEL,
            parts = listOf(Part(text = "unsigned", thought = true), Part(text = "answer")),
        ))), stream = false).toList()

        val content = JSONObject(server.takeRequest().body.readUtf8())
            .getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(1, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("answer", content.getJSONObject(0).getString("text"))
    }

    @Test
    fun serviceErrorsKeepTheExistingErrorResponseContract() = runBlocking {
        for (stream in listOf(false, true)) {
            server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                .setBody("""{"type":"error","error":{"type":"invalid_request_error","message":"invalid config"}}"""))

            val response = model.generateContent(request(), stream).toList().single()

            assertTrue(response.errorMessage.orEmpty().contains("400"))
        }
    }

    @Test
    fun cancellingPendingStreamingAndUnaryCallsStopsWaiting() = runBlocking {
        for (stream in listOf(true, false)) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val pending = async(Dispatchers.IO) { model.generateContent(request(), stream).toList() }
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                }
                withTimeout(5_000) { pending.cancelAndJoin() }
                assertTrue(pending.isCancelled)
            } finally {
                pending.cancel()
            }
        }
    }

    private fun request() = LlmRequest(contents = listOf(
        Content(role = Role.USER, parts = listOf(Part(text = "hello"))),
    ))

    private fun enqueueStream(vararg events: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(events.joinToString("") { event ->
                "event: ${JSONObject(event).getString("type")}\ndata: $event\n\n"
            }))
    }

    private fun enqueueMessage() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"id":"msg-2","type":"message","role":"assistant","model":"claude-sonnet-4-6","content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}""",
        ))
    }

    private fun start() =
        """{"type":"message_start","message":{"id":"msg-1","type":"message","role":"assistant","model":"claude-sonnet-4-6","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":0}}}"""

    private fun finish(reason: String = "end_turn") =
        """{"type":"message_delta","delta":{"stop_reason":"$reason","stop_sequence":null},"usage":{"output_tokens":2}}"""
}
