package github.ponyhuang.gimi.data.agent

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.ThinkingLevel
import com.google.adk.kt.types.Tool
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.data.agent.model.Claude
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
    fun ordinaryToolNamesArePreservedWithoutProviderSpecificRewriting() {
        val body = capture("claude-sonnet-4-6", LlmRequest(
            contents = listOf(userContent()),
            config = GenerateContentConfig(tools = listOf(Tool(functionDeclarations = listOf(
                FunctionDeclaration(name = "web_search", description = "Search"),
                FunctionDeclaration(name = "custom_clock_42", description = "Clock"),
            )))),
        ))

        val tools = body["tools"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("web_search", "custom_clock_42"), tools.map { it["name"].text() })
        assertTrue(tools.all { "input_schema" in it && it["type"].text() != "web_search_20250305" })
        assertTrue(tools.all { it["input_schema"]!!.jsonObject["properties"] is JsonObject })
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
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(MESSAGE_RESPONSE),
        )
        try {
            runBlocking { claude.generateContent(request, stream = false).toList() }
            return Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
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
