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
}
