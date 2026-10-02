package github.ponyhuang.gimi.data.agent.recommendation

import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCapability
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationContext
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationGenerationInput
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.adk.kt.types.Type
import org.json.JSONArray
import org.json.JSONObject

class RecommendationOutputParserTest {
    @Test
    fun rejectsMissingOrUnknownCategoryInsteadOfDefaulting() {
        val missingCategory = """{"recommendations":[
            {"prompt":"任务1"},
            {"prompt":"任务2","category":"vision"},
            {"prompt":"任务3","category":"research"},
            {"prompt":"任务4","category":"writing"},
            {"prompt":"任务5","category":"device"},
            {"prompt":"任务6","category":"productivity"}
        ]}""".trimIndent()
        val unknownCategory = """{"recommendations":[
            {"prompt":"任务1","category":"telepathy"},
            {"prompt":"任务2","category":"vision"},
            {"prompt":"任务3","category":"research"},
            {"prompt":"任务4","category":"writing"},
            {"prompt":"任务5","category":"device"},
            {"prompt":"任务6","category":"productivity"}
        ]}""".trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            RecommendationOutputParser.parse(missingCategory)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationOutputParser.parse(unknownCategory)
        }
    }

    @Test
    fun rejectsTopLevelArrayAliasedKeysAndMarkdownFences() {
        // 结构契约已写进提示，别名兜底会把契约违约伪装成成功，因此必须显式拒绝。
        val aliasedArray = """[
            {"task":"任务1","suggestion":"建议1"},
            {"task":"任务2","suggestion":"建议2"},
            {"task":"任务3","suggestion":"建议3"},
            {"task":"任务4","suggestion":"建议4"},
            {"task":"任务5","suggestion":"建议5"},
            {"task":"任务6","suggestion":"建议6"}
        ]""".trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            RecommendationOutputParser.parse(aliasedArray)
        }
        // JSON 输出契约不接受 Markdown；围栏内使用合法内容以免其它校验掩盖格式问题。
        val validJson = """{"recommendations":[
            {"prompt":"任务1","category":"reasoning"},
            {"prompt":"任务2","category":"vision"},
            {"prompt":"任务3","category":"research"},
            {"prompt":"任务4","category":"writing"},
            {"prompt":"任务5","category":"device"},
            {"prompt":"任务6","category":"productivity"}
        ]}""".trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationOutputParser.parse("```json\n$validJson\n```")
        }
    }

    @Test
    fun rejectsPromptBeyondDisplayLengthLimit() {
        val oversized = """{"recommendations":[
            {"prompt":"${"很".repeat(161)}","category":"general"},
            {"prompt":"任务2","category":"vision"},
            {"prompt":"任务3","category":"research"},
            {"prompt":"任务4","category":"writing"},
            {"prompt":"任务5","category":"device"},
            {"prompt":"任务6","category":"productivity"}
        ]}""".trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            RecommendationOutputParser.parse(oversized)
        }
    }

    @Test
    fun parsesExtraFieldsWithoutFailing() {
        val raw = """{"recommendations":[
            {"prompt":"任务1","category":"reasoning","reason":"模型附带的解释"},
            {"prompt":"任务2","category":"vision"},
            {"prompt":"任务3","category":"research"},
            {"prompt":"任务4","category":"writing"},
            {"prompt":"任务5","category":"device"},
            {"prompt":"任务6","category":"productivity"}
        ]}""".trimIndent()

        val result = RecommendationOutputParser.parse(raw)

        assertEquals(RecommendationSnapshot.RECOMMENDATION_COUNT, result.size)
        assertEquals("任务1", result.first().prompt)
        assertEquals("recommendation-1", result.first().id)
        assertEquals("recommendation-6", result.last().id)
        assertEquals(RecommendationCategory.REASONING, result.first().category)
    }

    @Test
    fun rejectsWrongSizedOutput() {
        val undersized = """{"recommendations":[
            {"prompt":"1","category":"general"},
            {"prompt":"2","category":"general"},
            {"prompt":"3","category":"general"},
            {"prompt":"4","category":"general"},
            {"prompt":"5","category":"general"}]}
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            RecommendationOutputParser.parse(undersized)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationOutputParser.parse("""{"recommendations":[]}""")
        }
    }

    @Test
    fun promptContainsSystemInstructionCapabilitiesAndContext() {
        val input = RecommendationGenerationInput(
            systemInstruction = "system rules",
            capabilities = listOf(
                RecommendationCapability("clock", "local", "Read time"),
                RecommendationCapability("search", "mcp:research", "Search MCP catalog"),
            ),
            context = RecommendationContext(mapOf("locale" to "zh-CN")),
        )

        val prompt = RecommendationPromptBuilder.build(input)

        assertTrue(prompt.contains("system rules"))
        assertTrue(prompt.contains("clock"))
        assertTrue(prompt.contains("Read time"))
        assertTrue(prompt.contains("mcp:research"))
        assertTrue(prompt.contains("Search MCP catalog"))
        assertTrue(prompt.contains("zh-CN"))
        assertTrue(prompt.contains("exactly ${RecommendationSnapshot.RECOMMENDATION_COUNT}"))
    }

    @Test
    fun structuredOutputConfigRequiresRecommendationJsonShape() {
        val schema = RecommendationOutputFormat.config.responseSchema

        assertEquals("application/json", RecommendationOutputFormat.config.responseMimeType)
        assertEquals(Type.OBJECT, schema?.type)
        assertEquals(Type.ARRAY, schema?.properties?.get("recommendations")?.type)
        assertEquals(2, schema?.properties?.get("recommendations")?.items?.properties?.size)
    }

    @Test
    fun recommendationToolResultsAreConvertedToJsonNativeValues() {
        val result = RecommendationToolResultSanitizer.sanitize(
            mapOf("items" to JSONArray().put(JSONObject().put("name", "item"))),
        ) as Map<*, *>

        assertEquals(listOf(mapOf("name" to "item")), result["items"])
    }
}
