package github.ponyhuang.gimi.agent.recommendation

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
    fun parsesRecommendationsFromFencedJson() {
        val raw = """
            ```json
            {"recommendations":[
              {"prompt":"任务1","category":"reasoning"},
              {"prompt":"任务2","category":"vision"},
              {"prompt":"任务3","category":"research"},
              {"prompt":"任务4","category":"writing"},
              {"prompt":"任务5","category":"device"},
              {"prompt":"任务6","category":"productivity"}
            ]}
            ```
        """.trimIndent()

        val result = RecommendationOutputParser.parse(raw)

        assertEquals(RecommendationSnapshot.RECOMMENDATION_COUNT, result.size)
        assertEquals("任务1", result.first().prompt)
        assertEquals("recommendation-1", result.first().id)
        assertEquals("recommendation-6", result.last().id)
    }

    @Test
    fun parsesTopLevelTaskSuggestionArrayWithoutCategories() {
        val raw = """
            [
              {"task":"任务1","suggestion":"建议1"},
              {"task":"任务2","suggestion":"建议2"},
              {"task":"任务3","suggestion":"建议3"},
              {"task":"任务4","suggestion":"建议4"},
              {"task":"任务5","suggestion":"建议5"},
              {"task":"任务6","suggestion":"建议6"}
            ]
        """.trimIndent()

        val result = RecommendationOutputParser.parse(raw)

        assertEquals(RecommendationSnapshot.RECOMMENDATION_COUNT, result.size)
        assertEquals("任务1", result.first().prompt)
        assertEquals(RecommendationCategory.GENERAL, result.first().category)
    }

    @Test
    fun rejectsDuplicateOrWrongSizedOutput() {
        val duplicated = """{"recommendations":[
            {"prompt":"same","category":"general"},
            {"prompt":"same","category":"general"},
            {"prompt":"3","category":"general"},
            {"prompt":"4","category":"general"},
            {"prompt":"5","category":"general"}]}
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            RecommendationOutputParser.parse(duplicated)
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
    fun promptRequiresUsefulRecommendationsInsteadOfTrivialStatusQueries() {
        val prompt = RecommendationPromptBuilder.build(
            RecommendationGenerationInput(
                systemInstruction = "system rules",
                capabilities = emptyList(),
                context = RecommendationContext(emptyMap()),
            ),
        )

        assertTrue(prompt.contains("meaningful"))
        assertTrue(prompt.contains("directly visible status"))
        assertTrue(prompt.contains("multi-step"))
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
