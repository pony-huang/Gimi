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
        assertEquals(11, plugin.toolCount)
        val toolset = plugin.toolSets().single()
        assertEquals(
            listOf(
                "v2ex_notifications",
                "v2ex_notification_delete",
                "v2ex_me",
                "v2ex_token",
                "v2ex_token_create",
                "v2ex_node",
                "v2ex_node_topics",
                "v2ex_topic",
                "v2ex_topic_replies",
                "v2ex_topic_set_sticky",
                "v2ex_topic_boost",
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
        assertTrue(instructions.contains("v2ex_notifications"))
        assertTrue(instructions.contains("v2ex_topic_replies"))
        assertTrue(instructions.contains("Personal Access Token"))
    }

    @Test
    fun configureStoresTokenAndFallsBackToDefaultBaseUrlOnBlank() {
        val plugin = V2exPlugin()

        plugin.configure(mapOf(V2exPlugin.KEY_TOKEN to "secret-pat", V2exPlugin.KEY_BASE_URL to ""))
        assertEquals("secret-pat", plugin.apiToken())
        assertEquals(V2exApi.DEFAULT_BASE_URL, plugin.apiBaseUrl())

        plugin.configure(mapOf(V2exPlugin.KEY_BASE_URL to "https://global.v2ex.co/api/v2", V2exPlugin.KEY_TOKEN to ""))
        assertEquals("", plugin.apiToken())
        assertEquals("https://global.v2ex.co/api/v2", plugin.apiBaseUrl())
    }
}
