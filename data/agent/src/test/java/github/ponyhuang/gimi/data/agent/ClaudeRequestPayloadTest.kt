package github.ponyhuang.gimi.data.agent

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.adk.kt.models.LlmRequest
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
import github.ponyhuang.gimi.data.agent.model.Claude
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Claude 适配器实际发出的 HTTP 请求体契约。
 *
 * 断言真实的 wire 形态（cache 断点、结构化输出、thinking 模式），因此换 SDK 版本或改
 * 映射会在这里暴露，而不是等到线上请求被拒绝才发现。响应由本地服务伪造，没有真实调用。
 */
class ClaudeRequestPayloadTest {

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
    fun interruptedToolCallsHaveErrorResultsBeforeTheNextUserInput() {
        val calls = listOf("open", "check").map { id ->
            FunctionCall(id = id, name = id, args = emptyMap())
        }
        // 包括整批取消、部分完成、正常完成，以及末尾尚无新输入的历史。
        for (completedCalls in listOf(emptyList(), calls.take(1), calls.takeLast(1), calls)) {
            for (nextInput in listOf("男的女的", null)) {
                val completed = completedCalls.map { call ->
                    Part(functionResponse = FunctionResponse(
                        id = call.id, name = call.name, response = mapOf("result" to "done"),
                    ))
                }
                val history = buildList {
                    add(userContent())
                    add(Content(role = Role.MODEL, parts = calls.map { Part(functionCall = it) }))
                    val parts = listOfNotNull(nextInput?.let { Part(text = it) }) + completed
                    if (parts.isNotEmpty()) add(Content(role = Role.USER, parts = parts))
                }

                val body = capture("deepseek-flash", LlmRequest(contents = history))

                val messages = body["messages"]!!.jsonArray
                assertEquals("Every tool_use needs an immediately following user result: $body", 3, messages.size)
                val blocks = messages[2].jsonObject["content"]!!.jsonArray.map { it.jsonObject }
                assertEquals(setOf("open", "check"), blocks.take(2).map { it["tool_use_id"].text() }.toSet())
                blocks.take(2).forEach { result ->
                    val completed = completedCalls.any { it.id == result["tool_use_id"].text() }
                    assertEquals("tool_result", result["type"].text())
                    assertEquals((!completed).toString(), result["is_error"].text())
                    if (completed) assertEquals("\"done\"", result["content"].text())
                }
                assertEquals(nextInput, blocks.drop(2).singleOrNull()?.get("text").text())
                // 请求规范化只修复本次发送的副本，不能把占位错误持久化成真实执行结果。
                assertEquals(completedCalls.size, history.flatMap { it.parts }.count { it.functionResponse != null })
            }
        }
    }

    @Test
    fun searchedToolsAreDefinedAfterEmptyResultsWithoutChangingTopLevelTools() {
        val request = inlineSearchRequest(listOf("clock"), "clock")
        val body = capture("deepseek-flash", request)

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
        assertNull(lastBetaHeader)
    }

    @Test
    fun followupAfterInterruptedModelKeepsSystemAfterUserResults() {
        val request = inlineSearchRequest(listOf("clock"), "clock")
        val body = capture("deepseek-flash", request.copy(contents = request.contents + userContent()))
        assertEquals(listOf("user", "assistant", "user", "system"),
            body["messages"]!!.jsonArray.map { it.jsonObject["role"].text() })
        assertEquals(4, request.contents.size)
    }

    @Test
    fun unadaptedEndpointDoesNotReceiveInlineBlocksOrBetaHeader() {
        val body = capture("claude-opus-5-5", inlineSearchRequest(listOf("clock"), "clock"))
        assertFalse(body.toString().contains("tool_addition"))
        assertFalse(body.toString().contains("defer_loading"))
        assertEquals(listOf("tool_search", "clock"), body["tools"]!!.jsonArray.map { it.jsonObject["name"].text() })
        assertNull(lastBetaHeader)
    }

    @Test
    fun deferredCatalogIsDeclaredBeforeTheFirstSearch() {
        val body = capture("deepseek-flash", inlineSearchRequest(listOf("clock")))
        val clock = body["tools"]!!.jsonArray.last().jsonObject
        assertEquals("clock", clock["name"].text())
        assertEquals("true", clock["defer_loading"].text())
        assertFalse(body.toString().contains("tool_addition"))
    }

    @Test
    fun replacingSearchSelectionUsesCurrentDeclarationsWithoutUnsupportedRemovalBlocks() {
        val request = inlineSearchRequest(listOf("calendar"), "clock", "calendar")
        val body = capture("deepseek-flash", request)
        assertFalse(body.toString().contains("tool_removal"))
        assertFalse(body.toString().contains("tool_addition"))
        assertFalse(body.toString().contains("defer_loading"))
        assertEquals(listOf("tool_search", "calendar"), body["tools"]!!.jsonArray.map { it.jsonObject["name"].text() })
        assertEquals(7, request.contents.size)
    }

    @Test
    fun disabledSearchedToolIsWithdrawnBeforeContinuing() {
        val body = capture("deepseek-flash", inlineSearchRequest(emptyList(), "clock"))
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
        val body = capture("deepseek-flash", request.copy(contents = history))
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
        val body = capture("deepseek-flash", inlineSearchRequest(listOf("clock", "calendar"), "clock", "clock", "calendar"))
        val additions = body["messages"]!!.jsonArray.flatMap { it.jsonObject["content"]!!.jsonArray }
            .map { it.jsonObject }.filter { it["type"].text() == "tool_addition" }
        assertEquals(listOf("clock", "calendar"), additions.map { it["tool"]!!.jsonObject["name"].text() })
        assertFalse(body.toString().contains("tool_removal"))
        assertTrue(body.toString().contains("defer_loading"))
    }

    private var lastBetaHeader: String? = null

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
                        "selkie.inline_tool_definitions" to listOf(
                            """{"name":"$name","description":"Read $name","parameters":{"type":"OBJECT","properties":{}}}""",
                        ),
                    ),
                ))))
            }
        }
        val catalog = (enabled + searches.toList()).distinct().map {
            com.google.adk.kt.serialization.adkJson.encodeToString(FunctionDeclaration.serializer(),
                FunctionDeclaration(name = it, description = "Read $it"))
        }
        return LlmRequest(contents = contents, config = GenerateContentConfig(
            systemInstruction = Content(parts = listOf(Part(partMetadata = mapOf(
                "selkie.deferred_tool_catalog" to catalog,
            )))),
            tools = listOf(Tool(functionDeclarations = (listOf("tool_search") + enabled).map {
                FunctionDeclaration(name = it, description = "Read $it")
            })),
        ))
    }

    @Test
    fun nativeClaudeRequestsCarryCacheBreakpointsOnSystemToolsAndLastMessage() {
        val body = capture("claude-sonnet-4-5", requestWithTool())

        assertEquals(body.toString(), "ephemeral", body["system"]!!.jsonArray.single().jsonObject.cacheType())
        assertEquals(body.toString(), "ephemeral", body["tools"]!!.jsonArray.last().jsonObject.cacheType())
        assertEquals(
            body.toString(),
            "ephemeral",
            body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray
                .last().jsonObject.cacheType(),
        )
    }

    @Test
    fun anthropicCompatibleThirdPartyEndpointsGetNoCacheControl() {
        val body = capture("MiniMax-Text-01", requestWithTool())

        assertFalse("未识别的兼容端点不得收到 cache_control：$body", "cache_control" in body.toString())
    }

    @Test
    fun sameNamedLocalFunctionKeepsItsOrdinaryShape() {
        val body = capture("glm-4.6", toolsRequest("web_search", "custom_clock_42"))

        val tools = body["tools"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("web_search", "custom_clock_42"), tools.map { it["name"].text() })
        assertTrue(tools.all { "input_schema" in it && it["type"].text() != "web_search_20250305" })
        assertTrue(tools.all { it["input_schema"]!!.jsonObject["properties"] is JsonObject })
    }

    @Test
    fun providerDeclaredWebSearchKeepsItsVendorShape() {
        val body = capture("MiniMax-M3", toolsRequest(OfficialToolIds.PROVIDER_WEB_SEARCH))

        val tool = body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("web_search_20250305", tool["type"].text())
        assertEquals("web_search", tool["name"].text())
        assertNull("厂商原生声明不带 input_schema", tool["input_schema"])
    }

    @Test
    fun nativeServerToolDoesNotTakeTheToolCacheBreakpoint() {
        // 厂商原生声明没有可标记的 cache_control；最后一项是它时不得崩，也不得把断点打在它上面。
        val body = capture(
            "claude-sonnet-4-5",
            toolsRequest("clock", OfficialToolIds.PROVIDER_WEB_SEARCH),
        )

        val tools = body["tools"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("clock", "web_search"), tools.map { it["name"].text() })
        assertNull(tools.last()["cache_control"])
        assertNull(tools.first()["cache_control"])
    }

    @Test
    fun inlineImageAndRemotePdfKeepTheirProtocolShapes() {
        val body = capture("compatible-model", LlmRequest(contents = listOf(Content(
            role = Role.USER,
            parts = listOf(
                Part(inlineData = Blob(mimeType = "image/png", data = byteArrayOf(1, 2, 3))),
                Part(fileData = FileData(mimeType = "application/pdf", fileUri = "https://example.com/report.pdf")),
            ),
        ))))

        val parts = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        val image = parts[0].jsonObject
        assertEquals("image", image["type"].text())
        assertEquals("image/png", image["source"]!!.jsonObject["media_type"].text())
        assertEquals("AQID", image["source"]!!.jsonObject["data"].text())
        val pdf = parts[1].jsonObject
        assertEquals("document", pdf["type"].text())
        assertEquals("url", pdf["source"]!!.jsonObject["type"].text())
        assertEquals("https://example.com/report.pdf", pdf["source"]!!.jsonObject["url"].text())
    }

    @Test
    fun constructorDefaultMaxTokensCanBeOverriddenByRequest() {
        val request = LlmRequest(contents = listOf(userContent()))
        assertEquals("4096", capture("compatible-model", request, maxTokens = 4096)["max_tokens"].text())
        val override = request.copy(config = GenerateContentConfig(maxOutputTokens = 2048))
        assertEquals("2048", capture("compatible-model", override, maxTokens = 4096)["max_tokens"].text())
    }

    @Test
    fun structuredOutputAdaptsTheResponseSchemaForClaude() {
        val body = capture(
            "claude-sonnet-4-5",
            LlmRequest(
                contents = listOf(userContent()),
                config = GenerateContentConfig(
                    responseMimeType = "application/json",
                    responseSchema = Schema(
                        type = Type.OBJECT,
                        properties = mapOf(
                            "title" to Schema(type = Type.STRING, minLength = 3),
                            "count" to Schema(type = Type.INTEGER),
                        ),
                        required = listOf("title"),
                    ),
                ),
            ),
        )

        val format = body["output_config"]?.jsonObject?.get("format")?.jsonObject
        assertEquals(body.toString(), "json_schema", format?.get("type").text())
        val schema = format?.get("schema")!!.jsonObject
        assertEquals("false", schema["additionalProperties"].text())
        // minLength 会被 Claude 拒绝，改写进 description 而不是原样下发。
        val title = schema["properties"]!!.jsonObject["title"]!!.jsonObject
        assertNull(title["minLength"])
        assertTrue(title.toString(), title["description"]!!.jsonPrimitive.content.contains("minLength"))
    }

    @Test
    fun jsonMimeWithoutSchemaAsksForNoOutputFormat() {
        val body = capture(
            "claude-sonnet-4-5",
            LlmRequest(
                contents = listOf(userContent()),
                config = GenerateContentConfig(responseMimeType = "application/json"),
            ),
        )

        assertNull("只有 responseMimeType 时不得下发 output_config", body["output_config"])
    }

    @Test
    fun newerClaudeFamiliesUseAdaptiveThinkingWithSummarizedDisplay() {
        val body = capture("claude-sonnet-4-6", requestWithThinking(ThinkingLevel.MEDIUM))

        assertEquals(body.toString(), "adaptive", body["thinking"]!!.jsonObject["type"].text())
        assertEquals(body.toString(), "summarized", body["thinking"]!!.jsonObject["display"].text())
        assertEquals("medium", body["output_config"]!!.jsonObject["effort"].text())
    }

    @Test
    fun claude45KeepsManualThinkingForAllFourLevels() {
        val budgets = mapOf(
            ThinkingLevel.MINIMAL to "1024",
            ThinkingLevel.LOW to "2048",
            ThinkingLevel.MEDIUM to "4096",
            ThinkingLevel.HIGH to "6144",
        )
        for ((level, budget) in budgets) {
            val body = capture("claude-sonnet-4-5", requestWithThinking(level))
            assertEquals("enabled", body["thinking"]!!.jsonObject["type"].text())
            assertEquals(budget, body["thinking"]!!.jsonObject["budget_tokens"].text())
            assertNull(body["output_config"])
        }
    }

    @Test
    fun datedClaude4IdsDoNotTreatTheDateAsAMinorVersion() {
        for (model in listOf("claude-sonnet-4-20250514", "claude-opus-4-20250514")) {
            val body = capture(model, requestWithThinking(ThinkingLevel.MEDIUM))
            assertEquals(model, "enabled", body["thinking"]!!.jsonObject["type"].text())
            assertEquals("4096", body["thinking"]!!.jsonObject["budget_tokens"].text())
        }
    }

    @Test
    fun adaptiveModelsMapFourLevelsToSupportedEfforts() {
        // Claude adaptive 没有 minimal；最低两档都对应 low，固定预算模型仍保留四档。
        for ((level, effort) in mapOf(
            ThinkingLevel.MINIMAL to "low", ThinkingLevel.LOW to "low",
            ThinkingLevel.MEDIUM to "medium", ThinkingLevel.HIGH to "high",
        )) {
            val body = capture("claude-opus-4-6", requestWithThinking(level))
            assertEquals(effort, body["output_config"]!!.jsonObject["effort"].text())
        }
    }

    @Test
    fun explicitBudgetOverridesLevelAndModelInference() {
        val body = capture("claude-sonnet-4-6", requestWithBudget(3072))
        assertEquals("enabled", body["thinking"]!!.jsonObject["type"].text())
        assertEquals("3072", body["thinking"]!!.jsonObject["budget_tokens"].text())
    }

    @Test
    fun zeroBudgetDisablesThinking() {
        val body = capture("claude-sonnet-4-6", requestWithBudget(0))
        assertEquals("disabled", body["thinking"]!!.jsonObject["type"].text())
        assertNull(body["output_config"])
    }

    @Test
    fun negativeBudgetExplicitlyEnablesAdaptiveForAnAlias() {
        val body = capture("private-alias", requestWithBudget(-1))
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"].text())
        assertEquals("summarized", body["thinking"]!!.jsonObject["display"].text())
        assertEquals("high", body["output_config"]!!.jsonObject["effort"].text())
    }

    @Test
    fun explicitInvalidBudgetsFailBeforeSending() {
        for (budget in listOf(512, 8196)) {
            assertThrows(IllegalArgumentException::class.java) {
                capture("claude-3-7-sonnet", requestWithBudget(budget))
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun inferredBudgetStaysBelowMaxOutputTokens() {
        val body = capture("compatible-model", requestWithThinking(ThinkingLevel.HIGH).copy(
            config = GenerateContentConfig(
                maxOutputTokens = 2048,
                thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.HIGH),
            ),
        ))
        assertEquals("2047", body["thinking"]!!.jsonObject["budget_tokens"].text())
    }

    @Test
    fun samplingAndStopSequencesAreForwardedWithoutThinking() {
        val body = capture("compatible-model", LlmRequest(
            contents = listOf(userContent()),
            config = GenerateContentConfig(temperature = 0.2f, topP = 0.8f, topK = 10, stopSequences = listOf("END")),
        ))
        assertTrue(body.containsKey("temperature"))
        assertTrue(body.containsKey("top_p"))
        assertEquals("10", body["top_k"].text())
        assertEquals("END", body["stop_sequences"]!!.jsonArray.single().text())
    }

    @Test
    fun thinkingOmitsSamplingAndPreservesStructuredOutputWithEffort() {
        val body = capture("claude-sonnet-4-6", LlmRequest(
            contents = listOf(userContent()),
            config = GenerateContentConfig(
                temperature = 0.2f, topP = 0.8f, topK = 10,
                thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.HIGH),
                responseMimeType = "application/json",
                responseSchema = Schema(type = Type.OBJECT, properties = mapOf("answer" to Schema(type = Type.STRING))),
            ),
        ))
        assertFalse(body.containsKey("temperature"))
        assertFalse(body.containsKey("top_p"))
        assertFalse(body.containsKey("top_k"))
        val output = body["output_config"]!!.jsonObject
        assertEquals("high", output["effort"].text())
        assertEquals("json_schema", output["format"]!!.jsonObject["type"].text())
    }

    @Test
    fun olderClaudeFamiliesKeepTheFixedThinkingBudget() {
        val body = capture("claude-3-7-sonnet-20250219", requestWithThinking(ThinkingLevel.MEDIUM))

        assertEquals(body.toString(), "enabled", body["thinking"]!!.jsonObject["type"].text())
        assertEquals(body.toString(), "4096", body["thinking"]!!.jsonObject["budget_tokens"].text())
    }

    @Test
    fun disablingThoughtsSendsDisableEvenForNewerFamilies() {
        val body = capture(
            "claude-sonnet-4-5",
            LlmRequest(
                contents = listOf(userContent()),
                config = GenerateContentConfig(
                    thinkingConfig = ThinkingConfig(
                        thinkingLevel = ThinkingLevel.HIGH,
                        includeThoughts = false,
                    ),
                ),
            ),
        )

        assertEquals(body.toString(), "disabled", body["thinking"]!!.jsonObject["type"].text())
    }

    private fun requestWithTool() = LlmRequest(
        contents = listOf(userContent()),
        config = GenerateContentConfig(
            systemInstruction = Content(role = Role.SYSTEM, parts = listOf(Part(text = "Be brief."))),
            tools = listOf(
                Tool(
                    functionDeclarations = listOf(
                        FunctionDeclaration(
                            name = "clock",
                            description = "Reads the clock.",
                            parameters = Schema(type = Type.OBJECT, properties = emptyMap()),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun toolsRequest(vararg names: String) = LlmRequest(
        contents = listOf(userContent()),
        config = GenerateContentConfig(
            tools = listOf(Tool(functionDeclarations = names.map {
                FunctionDeclaration(name = it, description = it)
            })),
        ),
    )

    private fun requestWithThinking(level: ThinkingLevel) = LlmRequest(
        contents = listOf(userContent()),
        config = GenerateContentConfig(thinkingConfig = ThinkingConfig(thinkingLevel = level)),
    )

    private fun requestWithBudget(budget: Int) = LlmRequest(
        contents = listOf(userContent()),
        config = GenerateContentConfig(thinkingConfig = ThinkingConfig(
            thinkingBudget = budget, thinkingLevel = ThinkingLevel.HIGH,
        )),
    )

    private fun userContent() = Content(role = Role.USER, parts = listOf(Part(text = "你好")))

    /** 走一次真实客户端调用，返回它实际发出的请求体。 */
    private fun capture(model: String, request: LlmRequest, maxTokens: Int = 8196): JsonObject {
        val client = AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl(server.url("/").toString())
            .build()
        val claude = Claude(
            name = model,
            client = client,
            maxTokens = maxTokens,
            requestConverter = github.ponyhuang.gimi.data.agent.tools.search.AnthropicToolLoadingRequestConverter(
                model, maxTokens,
                github.ponyhuang.gimi.data.agent.tools.search.toolLoadingMode(
                    if (model.startsWith("deepseek")) ModelRuntimeMetadata(
                        "deepseek", github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.Anthropic,
                        model, "https://api.deepseek.com/anthropic",
                    ) else null,
                ),
            )::convert,
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(MESSAGE_RESPONSE),
        )
        try {
            val responses = runBlocking { claude.generateContent(request, stream = false).toList() }
            val sent = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
                ?: error("No HTTP request: ${responses.map { it.errorMessage }}")
            lastBetaHeader = sent.getHeader("anthropic-beta")
            return Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        } finally {
            client.close()
        }
    }

    private fun JsonObject.cacheType(): String? =
        get("cache_control")?.jsonObject?.get("type")?.text()

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.content

    private companion object {
        val MESSAGE_RESPONSE: String = """
            {
              "id": "msg_test",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-5",
              "content": [{"type": "text", "text": "ok"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
        """.trimIndent()
    }
}
