package github.ponyhuang.gimi.data.modelaccess.deepseek.llm

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import github.ponyhuang.gimi.domain.modelaccess.DEFERRED_CATALOG
import github.ponyhuang.gimi.domain.modelaccess.INLINE_DEFINITIONS
import github.ponyhuang.gimi.domain.modelaccess.ToolLoadingMode
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.serialization.adkJson
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Tool
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DeepSeek Messages 适配器实际发出的 HTTP 请求体契约：内联工具加载（tool_addition /
 * tool_reference）、latest_reminder 注入与会话标题参数。断言真实的 wire 形态，换 SDK
 * 版本或改映射会在这里暴露；响应由本地服务伪造，没有真实调用。
 */
class DeepSeekMessageRequestPayloadTest {

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
    fun searchedToolsAreDefinedAfterEmptyResultsWithoutChangingTopLevelTools() {
        val request = inlineSearchRequest(listOf("clock"), "clock")
        val body = capture(request)

        assertEquals("tool_search", body["tools"]!!.jsonArray.first().jsonObject["name"].text())
        val messages = body["messages"]!!.jsonArray
        val result = messages[2].jsonObject["content"]!!.jsonArray.single().jsonObject
        assertEquals("tool_result", result["type"].text())
        assertEquals("", result["content"].text())
        val addition = messages[3].jsonObject
        assertEquals("system", addition["role"].text())
        val block = addition["content"]!!.jsonArray.single().jsonObject
        assertEquals("tool_addition", block["type"].text())
        val tool = block["tool"]!!.jsonObject
        assertEquals("tool_reference", tool["type"].text())
        assertEquals("clock", tool["name"].text())
        val deferred = body["tools"]!!.jsonArray.last().jsonObject
        assertEquals("true", deferred["defer_loading"].text())
        assertEquals("object", deferred["input_schema"]!!.jsonObject["type"].text())
    }

    @Test
    fun followupAfterInterruptedModelKeepsSystemAfterUserResults() {
        val request = inlineSearchRequest(listOf("clock"), "clock")
        val body = capture(request.copy(contents = request.contents + userContent()))
        assertEquals(listOf("user", "assistant", "user", "system"),
            body["messages"]!!.jsonArray.map { it.jsonObject["role"].text() })
        assertEquals(4, request.contents.size)
    }

    @Test
    fun resultModeDoesNotReceiveInlineBlocks() {
        val request = inlineSearchRequest(listOf("clock"), "clock")
        val body = capture(request, loadingMode = ToolLoadingMode.RESULT)
        assertFalse(body.toString().contains("tool_addition"))
        assertFalse(body.toString().contains("defer_loading"))
        assertEquals(listOf("tool_search", "clock"), body["tools"]!!.jsonArray.map { it.jsonObject["name"].text() })
    }

    @Test
    fun deferredCatalogIsDeclaredBeforeTheFirstSearch() {
        val body = capture(inlineSearchRequest(listOf("clock")))
        val clock = body["tools"]!!.jsonArray.last().jsonObject
        assertEquals("clock", clock["name"].text())
        assertEquals("true", clock["defer_loading"].text())
        assertFalse(body.toString().contains("tool_addition"))
    }

    @Test
    fun replacingSearchSelectionUsesCurrentDeclarationsWithoutUnsupportedRemovalBlocks() {
        val request = inlineSearchRequest(listOf("calendar"), "clock", "calendar")
        val body = capture(request)
        assertFalse(body.toString().contains("tool_removal"))
        assertFalse(body.toString().contains("tool_addition"))
        assertFalse(body.toString().contains("defer_loading"))
        assertEquals(listOf("tool_search", "calendar"), body["tools"]!!.jsonArray.map { it.jsonObject["name"].text() })
        assertEquals(7, request.contents.size)
    }

    @Test
    fun disabledSearchedToolIsWithdrawnBeforeContinuing() {
        val body = capture(inlineSearchRequest(emptyList(), "clock"))
        assertFalse(body.toString().contains("tool_removal"))
        assertFalse(body.toString().contains("tool_addition"))
        assertFalse(body.toString().contains("defer_loading"))
        assertEquals(listOf("tool_search"), body["tools"]!!.jsonArray.map { it.jsonObject["name"].text() })
    }

    @Test
    fun withdrawingAToolPreservesItsHistoricalCallAndResult() {
        val request = inlineSearchRequest(listOf("calendar"), "clock", "calendar")
        val historicalCall = Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(
            id = "clock_call", name = "clock", args = emptyMap(),
        ))))
        val historicalResult = Content(role = Role.USER, parts = listOf(Part(functionResponse = FunctionResponse(
            id = "clock_call", name = "clock", response = mapOf("time" to "12:00"),
        ))))
        val history = request.contents.take(4) + listOf(historicalCall, historicalResult) + request.contents.drop(4)
        val body = capture(request.copy(contents = history))
        val blocks = body["messages"]!!.jsonArray.flatMap { it.jsonObject["content"]!!.jsonArray }
            .map { it.jsonObject }
        assertTrue(blocks.any { it["type"].text() == "tool_use" && it["id"].text() == "clock_call" })
        assertTrue(blocks.any { it["type"].text() == "tool_result" && it["tool_use_id"].text() == "clock_call" })
        assertFalse(body.toString().contains("tool_removal"))
        assertEquals(listOf("tool_search", "calendar"), body["tools"]!!.jsonArray.map { it.jsonObject["name"].text() })
        assertEquals(historicalCall, history[4])
    }

    @Test
    fun repeatedAndAdditiveSearchesOnlyDeclareNewToolReferences() {
        val body = capture(inlineSearchRequest(listOf("clock", "calendar"), "clock", "clock", "calendar"))
        val additions = body["messages"]!!.jsonArray.flatMap { it.jsonObject["content"]!!.jsonArray }
            .map { it.jsonObject }.filter { it["type"].text() == "tool_addition" }
        assertEquals(listOf("clock", "calendar"), additions.map { it["tool"]!!.jsonObject["name"].text() })
        assertFalse(body.toString().contains("tool_removal"))
        assertTrue(body.toString().contains("defer_loading"))
    }

    // ---- latest_reminder ----

    @Test
    fun latestReminderIsInjectedRightBeforeTheLatestUserMessage() {
        val request = LlmRequest(contents = listOf(userContent()))
        val body = capture(request, latestReminder = { FIXED_REMINDER })

        val messages = body["messages"]!!.jsonArray
        assertEquals(listOf("latest_reminder", "user"), messages.map { it.jsonObject["role"].text() })
        // content 必须是字符串（数组形态会被 422 拒绝）。
        assertEquals(FIXED_REMINDER, messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        // 提醒紧挨 user 之前，且 user 内容不受影响。
        assertEquals("你好", messages[1].jsonObject.contentText())
    }

    @Test
    fun onlyTheLatestTurnCarriesAReminderWithoutHistoryReplay() {
        val request = LlmRequest(contents = listOf(
            userContent("第一问"),
            Content(role = Role.MODEL, parts = listOf(Part(text = "答"))),
            userContent("第二问"),
        ))
        val body = capture(request, latestReminder = { FIXED_REMINDER })

        val roles = body["messages"]!!.jsonArray.map { it.jsonObject["role"].text() }
        // 不随历史回传：全文只有一条 latest_reminder，且紧挨最后一条 user 之前。
        assertEquals(listOf("user", "assistant", "latest_reminder", "user"), roles)
        assertEquals(1, body.toString().split("latest_reminder").size - 1)
    }

    @Test
    fun toolResultTurnsDoNotReceiveLatestReminder() {
        val request = LlmRequest(contents = listOf(
            userContent(),
            Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(
                id = "call_1", name = "clock", args = emptyMap(),
            )))),
            Content(role = Role.USER, parts = listOf(Part(functionResponse = FunctionResponse(
                id = "call_1", name = "clock", response = mapOf("time" to "12:00"),
            )))),
        ))
        val body = capture(request, latestReminder = { FIXED_REMINDER })
        // latest_reminder 不能夹在 tool_use 与 tool_result 之间：工具回传轮整轮不注入。
        assertFalse(body.toString().contains("latest_reminder"))
    }

    @Test
    fun blankReminderIsNotInjected() {
        val body = capture(LlmRequest(contents = listOf(userContent())), latestReminder = { "" })
        assertFalse(body.toString().contains("latest_reminder"))
    }

    @Test
    fun reminderContentFollowsTheContractedFormat() {
        val content = latestReminderContent(
            now = FIXED_TIMESTAMP,
            locale = java.util.Locale.SIMPLIFIED_CHINESE,
        )
        assertEquals("2026-11-15,星期日,,App,中文", content)
        val english = latestReminderContent(
            now = FIXED_TIMESTAMP,
            locale = java.util.Locale.ENGLISH,
        )
        assertEquals("2026-11-15,Sunday,,App,English", english)
    }

    @OptIn(com.google.adk.kt.annotations.FrameworkInternalApi::class)
    private fun inlineSearchRequest(enabled: List<String>, vararg searches: String): LlmRequest {
        val contents = buildList {
            add(userContent())
            searches.forEachIndexed { index, name ->
                add(Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(
                    id = "search_$index", name = "tool_search", args = mapOf("query" to name),
                )))))
                add(Content(role = Role.USER, parts = listOf(Part(functionResponse = FunctionResponse(
                    id = "search_$index", name = "tool_search", response = emptyMap(),
                )))))
                add(Content(role = Role.SYSTEM, parts = listOf(Part(
                    text = "", partMetadata = mapOf(
                        INLINE_DEFINITIONS to listOf(
                            """{"name":"$name","description":"Read $name","parameters":{"type":"OBJECT","properties":{}}}""",
                        ),
                    ),
                ))))
            }
        }
        val catalog = (enabled + searches.toList()).distinct().map {
            adkJson.encodeToString(FunctionDeclaration.serializer(),
                FunctionDeclaration(name = it, description = "Read $it"))
        }
        return LlmRequest(contents = contents, config = GenerateContentConfig(
            systemInstruction = Content(parts = listOf(Part(partMetadata = mapOf(
                DEFERRED_CATALOG to catalog,
            )))),
            tools = listOf(Tool(functionDeclarations = (listOf("tool_search") + enabled).map {
                FunctionDeclaration(name = it, description = "Read $it")
            })),
        ))
    }

    private fun userContent(text: String = "你好") =
        Content(role = Role.USER, parts = listOf(Part(text = text)))

    /** 走一次真实客户端调用，返回它实际发出的请求体。 */
    private fun capture(
        request: LlmRequest,
        loadingMode: ToolLoadingMode = ToolLoadingMode.DEEPSEEK_REFERENCE,
        latestReminder: (() -> String?)? = null,
    ): JsonObject {
        val client = AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl(server.url("/").toString())
            .build()
        val model = DeepSeekMessageChat(
            name = "deepseek-flash",
            client = client,
            requestConverter = DeepSeekMessageToolLoadingRequestConverter(
                "deepseek-flash", 8196, loadingMode, latestReminder,
            )::convert,
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(MESSAGE_RESPONSE),
        )
        try {
            val responses = runBlocking { model.generateContent(request, stream = false).toList() }
            val sent = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
                ?: error("No HTTP request: ${responses.map { it.errorMessage }}")
            return Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        } finally {
            client.close()
        }
    }

    private fun JsonObject.contentText(): String =
        (this["content"] as? JsonPrimitive)?.content
            ?: (this["content"] as? JsonArray)?.joinToString("") { block ->
                (block as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
            }
            .orEmpty()

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.content

    private companion object {
        // 2026-11-15 是星期日（GMT+8 上下文给出的锚点）。
        const val FIXED_REMINDER = "2026-11-15,星期日,,App,中文"
        val MESSAGE_RESPONSE: String = """
            {
              "id": "msg_test",
              "type": "message",
              "role": "assistant",
              "model": "deepseek-flash",
              "content": [{"type": "text", "text": "ok"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
        """.trimIndent()
    }
}

/** 与 [DeepSeekMessageRequestPayloadTest.reminderContentFollowsTheContractedFormat] 共用的固定时间戳：
 *  2026-11-15 12:00 UTC（= 当日 20:00 GMT+8），UTC 与 GMT+8 同日，均为星期日。 */
private const val FIXED_TIMESTAMP: Long = 1_794_744_000_000L
