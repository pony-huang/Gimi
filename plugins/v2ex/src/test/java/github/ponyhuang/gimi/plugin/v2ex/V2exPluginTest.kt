package github.ponyhuang.gimi.plugin.v2ex

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2exPluginTest {

    @Test
    fun pluginPublishesToolsThroughToolsetAndAppendsUsageInstructions() = runTest {
        val plugin = V2exPlugin()

        assertTrue(plugin.tools().isEmpty())
        assertEquals(7, plugin.toolCount)
        val toolset = plugin.toolSets().single()
        assertEquals(
            listOf(
                "v2ex_hot_topics",
                "v2ex_latest_topics",
                "v2ex_node_topics",
                "v2ex_topic",
                "v2ex_topic_replies",
                "v2ex_node_info",
                "v2ex_member_info",
            ),
            toolset.getTools(null).map { it.name },
        )

        val request = LlmRequest(
            config = GenerateContentConfig(
                systemInstruction = Content(parts = listOf(Part(text = "Base instruction"))),
            ),
        )
        val processed = toolset.processLlmRequest(mockk<ToolContext>(), request)
        val instructions = processed.config.systemInstruction
            ?.parts
            .orEmpty()
            .mapNotNull(Part::text)
            .joinToString("\n")

        assertTrue(instructions.contains("Base instruction"))
        assertTrue(instructions.contains("<v2ex>"))
        assertTrue(instructions.contains("v2ex_hot_topics"))
        assertTrue(instructions.contains("v2ex_topic_replies"))
        assertTrue(instructions.contains("base_url"))
    }

    @Test
    fun configureFallsBackToDefaultBaseUrlOnBlank() {
        val plugin = V2exPlugin()

        plugin.configure(mapOf(V2exPlugin.KEY_BASE_URL to ""))
        assertEquals(V2exApi.DEFAULT_BASE_URL, plugin.apiBaseUrl())

        plugin.configure(mapOf(V2exPlugin.KEY_BASE_URL to "https://global.v2ex.co/api"))
        assertEquals("https://global.v2ex.co/api", plugin.apiBaseUrl())
    }
}
