package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiToolContributionTest {
    @Test
    fun routingPrefersPurposeBuiltComponentsAndFallsBackToMarkdown() {
        val capabilities = A2uiCapabilities(
            catalogId = "catalog",
            schemaJson = """{
              "components": {
                "Text": {"description": "Plain text"},
                "Card": {"description": "Container"},
                "WeatherForecast": {"description": "Native multi-day weather card"},
                "WeatherToday": {"description": "Native current-day weather card"},
                "Video": {"description": "Native video player"}
              }
            }""",
        )

        val instruction = a2uiResponseRoutingInstructions(capabilities)

        assertTrue(instruction.contains("prefer the native A2UI result"))
        assertTrue(instruction.contains("normal Markdown response"))
        assertTrue(instruction.contains("- WeatherForecast"))
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
    fun newsDigestMentioningRainAndTemperatureDoesNotRequireWeatherCard() {
        val changes = """
            Render native NewsList component for today's news digest.
            Article: 12 provinces will see heavy rain and temperatures dropping by 8°C.
            Another article covers the weather forecast for northern China.
        """.trimIndent()

        assertFalse(requestedWeatherComponent(changes))
    }

    @Test
    fun explicitWeatherUiStillRequiresNativeWeatherComponent() {
        assertTrue(requestedWeatherComponent("Show a WeatherForecast card for Shanghai"))
        assertTrue(requestedWeatherComponent("展示上海明天的天气"))
        assertFalse(requestedWeatherComponent("Show a weather story inside a NewsList digest"))
        assertFalse(requestedWeatherComponent("Show a DataTable comparing historical weather observations"))
    }
}
