package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiToolContributionTest {
    @Test
    fun composableContainersEnableStructuredResultsWithoutTreatingThemAsDomainCards() {
        val instruction = a2uiResponseRoutingInstructions(A2uiCapabilities(
            "catalog", """{"components":{"Panel":{},"Disclosure":{},"Tabs":{},"DetailSheet":{},"Text":{}}}""",
        ))
        assertTrue(instruction.contains("COMPOSABLE UI"))
        assertTrue(instruction.contains("component_names"))
        assertTrue(instruction.contains("structured collections"))
        assertFalse(instruction.contains("- Panel"))
    }

    @Test
    fun routingPrefersPurposeBuiltComponentsAndFallsBackToMarkdown() {
        val capabilities = A2uiCapabilities(
            catalogId = "catalog",
            schemaJson = """{
              "components": {
                "Text": {"description": "Plain text"},
                "Card": {"description": "Container"},
                "WeatherToday": {"description": "Native current-day weather card"},
                "Video": {"description": "Native video player"}
              }
            }""",
        )

        val instruction = a2uiResponseRoutingInstructions(capabilities)

        assertTrue(instruction.contains("prefer the native A2UI result"))
        assertTrue(instruction.contains("normal Markdown response"))
        assertFalse(instruction.contains("- WeatherForecast"))
        assertTrue(instruction.contains("- WeatherToday"))
        assertTrue(instruction.contains("- Video"))
        assertFalse(instruction.contains("- Text"))
        assertFalse(instruction.contains("- Card"))
    }

    @Test
    fun routingWithOnlyGenericComponentsDefaultsToMarkdown() {
        val capabilities = A2uiCapabilities(
            catalogId = "catalog",
            schemaJson = """{"components":{"Text":{},"Card":{},"List":{}}}""",
        )

        val instruction = a2uiResponseRoutingInstructions(capabilities)

        assertTrue(instruction.contains("No purpose-built native components"))
        assertTrue(instruction.contains("prefer the normal Markdown response"))
    }

    @Test
    fun optionDeckRoutingCoversSocialAndNearbyPlaceResults() {
        val capabilities = A2uiCapabilities(
            catalogId = "catalog",
            schemaJson = """{
              "components": {
                "Text": {},
                "Card": {},
                "Panel": {},
                "OptionDeck": {
                  "description": "Compare plans, social posts, nearby stores or places one at a time"
                }
              }
            }""",
        )

        val instruction = a2uiResponseRoutingInstructions(capabilities)

        assertTrue(instruction.contains("OPTION DECK ROUTING"))
        assertTrue(instruction.contains("social notes"))
        assertTrue(instruction.contains("nearby place-search stores and places"))
        assertTrue(instruction.contains("Save / Skip"))
        assertTrue(instruction.contains("Want to visit / Not this place"))
        assertTrue(instruction.contains("\"OptionDeck\" in component_names"))
        assertFalse(
            a2uiResponseRoutingInstructions(
                A2uiCapabilities("catalog", """{"components":{"Text":{},"Card":{}}}"""),
            ).contains("OPTION DECK ROUTING"),
        )
    }

    @Test
    fun newsDigestMentioningRainAndTemperatureDoesNotRequireWeatherCard() {
        val changes = """
            Render today's news digest.
            Article: 12 provinces will see heavy rain and temperatures dropping by 8°C.
            Another article covers weather conditions in northern China.
        """.trimIndent()

        assertFalse(requestedWeatherComponent(changes, setOf("Panel")))
    }

    @Test
    fun explicitWeatherUiStillRequiresNativeWeatherComponent() {
        val weather = setOf("WeatherToday")
        assertTrue(requestedWeatherComponent("Show a WeatherToday card for Shanghai", weather))
        assertFalse(requestedWeatherComponent("展示上海明天的天气", weather))
        assertFalse(requestedWeatherComponent("展示上海未来七天天气", weather))
        assertFalse(requestedWeatherComponent("Show a weather story inside a news digest", weather))
        assertFalse(requestedWeatherComponent("Show a DataTable comparing historical weather observations", setOf("DataTable")))
        assertFalse(requestedWeatherComponent("Show an album of photos about today's weather", setOf("Panel", "Image")))
    }
}
