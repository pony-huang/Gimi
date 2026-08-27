package github.ponyhuang.gimi.plugin.zhihu

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

class ZhihuPluginTest {

    @Test
    fun pluginPublishesToolsThroughToolsetAndAppendsUsageInstructions() = runTest {
        val plugin = ZhihuPlugin()

        assertTrue(plugin.tools().isEmpty())
        assertEquals(4, plugin.toolCount)
        val toolset = plugin.toolSets().single()
        assertEquals(
            listOf("zhihu_search", "zhihu_global_search", "zhihu_hot_list", "zhihu_ask"),
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
        assertTrue(instructions.contains("<zhihu>"))
        assertTrue(instructions.contains("zhihu_search"))
        assertTrue(instructions.contains("zhihu_global_search"))
        assertTrue(instructions.contains("access_secret"))
    }
}
