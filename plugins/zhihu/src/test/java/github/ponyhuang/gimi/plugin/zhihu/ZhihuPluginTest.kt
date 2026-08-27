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
        assertEquals(12, plugin.toolCount)
        val toolset = plugin.toolSets().single()
        assertEquals(
            listOf(
                "zhihu_search",
                "zhihu_global_search",
                "zhihu_hot_list",
                "zhihu_ask",
                "zhihu_quota",
                "zhihu_knowledge_bases",
                "zhihu_knowledge_items",
                "zhihu_knowledge_search",
                "zhihu_knowledge_upload",
                "zhihu_pdf_parse",
                "zhihu_ppt_generate",
                "zhihu_task_status",
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
        assertTrue(instructions.contains("<zhihu>"))
        assertTrue(instructions.contains("zhihu_search"))
        assertTrue(instructions.contains("zhihu_global_search"))
        assertTrue(instructions.contains("access_secret"))
        assertTrue(instructions.contains("zhihu_quota"))
        assertTrue(instructions.contains("zhihu_knowledge_bases"))
        assertTrue(instructions.contains("zhihu_task_status"))
        assertTrue(instructions.contains("file_path"))
    }
}
