package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.models.*
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.domain.a2ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 模型调用计费与取消边界：仅格式错误允许一次纠错，其他失败立即传播。 */
class A2uiGenerationTest {
    private val capabilities = A2uiCapabilities("catalog", "{}")

    @Test
    fun retriesInvalidOutputOnceAndRestrictsSubagentToRenderTool() = runTest {
        val model = FakeModel()
        var validations = 0
        val protocol = object : A2uiProtocol {
            override suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities) {
                if (validations++ == 0) throw IllegalArgumentException("Invalid component")
            }
            override fun replay(records: List<A2uiRecord>) = records.filterIsInstance<A2uiRecord.Revision>().map { it.document }
        }
        val document = A2uiGeneration(model, protocol).generate("surface", capabilities, "Create form")
        assertEquals(2, model.requests.size)
        assertEquals("surface", document.surfaceId)
        assertEquals(listOf("render_a2ui"), model.requests[0].config.tools!!.flatMap { it.functionDeclarations.orEmpty() }.map { it.name })
        assertTrue(model.requests[1].contents.single().parts.single().text!!.contains("Invalid component"))
    }

    @Test
    fun exhaustedValidationStopsAfterTwoCalls() = runTest {
        val model = FakeModel()
        try {
            A2uiGeneration(model, rejectingProtocol()).generate("surface", capabilities, "Create")
            fail("Expected failure")
        } catch (_: IllegalArgumentException) { }
        assertEquals(2, model.requests.size)
    }

    @Test
    fun networkErrorsAndCancellationAreNeverRetried() = runTest {
        for (failure in listOf(IllegalStateException("Network failure"), CancellationException("Stopped"))) {
            val model = FakeModel(failure)
            try { A2uiGeneration(model, rejectingProtocol()).generate("surface", capabilities, "Create"); fail("Expected failure") }
            catch (actual: IllegalStateException) { assertSame(failure, actual) }
            assertEquals(1, model.requests.size)
        }
    }

    @Test
    fun weatherCapabilityAddsTodayOnlyRoutingAndPayloadExample() = runTest {
        val model = FakeModel()
        val schema = """{"components":{"WeatherToday":{}}}"""

        A2uiGeneration(model, acceptingProtocol()).generate(
            surfaceId = "surface",
            capabilities = A2uiCapabilities("catalog", schema),
            instruction = "Show today's Guangzhou weather",
            requestedComponents = setOf("WeatherToday"),
        )

        val prompt = model.requests.single().config.systemInstruction!!.parts.single().text!!
        assertTrue(prompt.contains("WEATHER COMPONENT ROUTING"))
        assertFalse(prompt.contains("WeatherForecast"))
        assertTrue(prompt.contains("WeatherToday"))
        assertTrue(prompt.contains("\"weather\":{\"path\":\"/weather\"}"))
        assertTrue(prompt.contains("exactly one item"))
    }

    @Test
    fun promptWithoutWeatherComponentsKeepsGenericCatalogBehavior() = runTest {
        val model = FakeModel()
        val schema = """{"components":{"Text":{}}}"""

        A2uiGeneration(model, acceptingProtocol()).generate(
            surfaceId = "surface",
            capabilities = A2uiCapabilities("catalog", schema),
            instruction = "Show a simple card",
        )

        val prompt = model.requests.single().config.systemInstruction!!.parts.single().text!!
        assertFalse(prompt.contains("WEATHER COMPONENT ROUTING"))
    }

    @Test
    fun musicPlayerCapabilityAddsRoutingAndSnapshotTiming() = runTest {
        val model = FakeModel()
        val schema = """{"components":{"MusicPlayer":{}}}"""

        A2uiGeneration(model, acceptingProtocol()).generate(
            surfaceId = "surface",
            capabilities = A2uiCapabilities("catalog", schema),
            instruction = "Show what is playing on Spotify",
            requestedComponents = setOf("MusicPlayer"),
        )

        val prompt = model.requests.single().config.systemInstruction!!.parts.single().text!!
        assertTrue(prompt.contains("MUSIC PLAYER ROUTING"))
        assertTrue(prompt.contains("provider"))
        assertTrue(prompt.contains("fetched_at_ms"))
        assertTrue(prompt.contains("playbackContext"))
        assertTrue(prompt.contains("Never invent progress"))
    }

    @Test
    fun optionDeckCapabilityAddsContextualActionsAndEventContract() = runTest {
        val model = FakeModel()
        val schema = """{"components":{"OptionDeck":{}}}"""

        A2uiGeneration(model, acceptingProtocol()).generate(
            surfaceId = "surface",
            capabilities = A2uiCapabilities("catalog", schema),
            instruction = "推荐几家周末值得试的餐厅",
            requestedComponents = setOf("OptionDeck"),
        )

        val prompt = model.requests.single().config.systemInstruction!!.parts.single().text!!
        assertTrue(prompt.contains("OPTION DECK ROUTING"))
        assertTrue(prompt.contains("\"deck\":{\"path\":\"/deck\"}"))
        assertTrue(prompt.contains("acceptLabel"))
        assertTrue(prompt.contains("rejectLabel"))
        assertTrue(prompt.contains("option_accept"))
        assertTrue(prompt.contains("option_exhausted"))
        assertTrue(prompt.contains("at least 2 and at most 12"))
    }

    @Test
    fun weatherRequestRejectsGenericCardAndRetriesWithNativeComponent() = runTest {
        val model = ScriptedModel(
            listOf(
                """[{"id":"root","component":"Card","child":"body"},
                    {"id":"body","component":"Text","text":"Weather"}]""",
                """[{"id":"root","component":"WeatherToday","weather":{"path":"/weather"}}]""",
            ),
        )
        val schema = """{"components":{"WeatherToday":{}}}"""

        A2uiGeneration(model, acceptingProtocol()).generate(
            surfaceId = "surface",
            capabilities = A2uiCapabilities("catalog", schema),
            instruction = "Show the weather forecast",
            requireWeatherComponent = true,
        )

        assertEquals(2, model.requests.size)
        val correction = model.requests[1].contents.single().parts.single().text!!
        assertTrue(correction.contains("Weather UI requests must use WeatherToday"))
    }

    @Test
    fun rejectsAnUnselectedNativeComponentAndValidatesCorrectionAgainstTheFullClientCatalog() = runTest {
        val schema = """{"components":{"Text":{},"Panel":{},"WeatherToday":{"description":"unused-weather-schema"}}}"""
        val full = A2uiCapabilities("catalog", schema)
        val model = ScriptedModel(listOf(
            """[{"id":"root","component":"WeatherToday"}]""",
            """[{"id":"root","component":"Text","text":"Result"}]""",
        ))
        var validated = 0
        val protocol = object : A2uiProtocol {
            override suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities) {
                validated += 1
                assertEquals(full, capabilities)
            }
            override fun replay(records: List<A2uiRecord>) = emptyList<A2uiDocument>()
        }
        A2uiGeneration(model, protocol).generate("surface", full, "Show a collection", requestedComponents = setOf("Panel"))
        assertEquals(2, model.requests.size)
        assertEquals(1, validated)
        val prompt = model.requests.first().config.systemInstruction!!.parts.single().text!!
        assertTrue(prompt.contains("mode: static"))
        assertFalse(prompt.contains("unused-weather-schema"))
        assertFalse(prompt.contains("WEATHER COMPONENT ROUTING"))
        assertFalse(prompt.contains("REQUIRED INPUT SELECTION"))
    }

    private fun rejectingProtocol() = object : A2uiProtocol {
        override suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities) {
            throw IllegalArgumentException("Rejected")
        }
        override fun replay(records: List<A2uiRecord>) = records.filterIsInstance<A2uiRecord.Revision>().map { it.document }
    }

    private fun acceptingProtocol() = object : A2uiProtocol {
        override suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities) = Unit
        override fun replay(records: List<A2uiRecord>) =
            records.filterIsInstance<A2uiRecord.Revision>().map { it.document }
    }

    private class FakeModel(private val failure: RuntimeException? = null) : Model {
        override val name = "test-model"
        val requests = mutableListOf<LlmRequest>()
        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
            requests += request
            failure?.let { throw it }
            emit(LlmResponse(content = Content(parts = listOf(Part(functionCall = FunctionCall(
                name = "render_a2ui", args = mapOf("components" to "[]", "data" to "{}"),
            ))))))
        }
    }

    private class ScriptedModel(private val components: List<String>) : Model {
        override val name = "scripted-model"
        val requests = mutableListOf<LlmRequest>()

        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
            val index = requests.size.coerceAtMost(components.lastIndex)
            requests += request
            emit(
                LlmResponse(
                    content = Content(
                        parts = listOf(
                            Part(
                                functionCall = FunctionCall(
                                    name = "render_a2ui",
                                    args = mapOf("components" to components[index], "data" to "{}"),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}
