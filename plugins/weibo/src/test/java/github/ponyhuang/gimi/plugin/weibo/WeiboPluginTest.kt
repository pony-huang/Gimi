package github.ponyhuang.gimi.plugin.weibo

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeiboPluginTest {

    @Test
    fun pluginPublishesToolsThroughToolsetAndAppendsUsageInstructions() = runTest {
        val plugin = WeiboPlugin()

        assertTrue(plugin.tools().isEmpty())
        assertEquals(11, plugin.toolCount)
        assertEquals("weibo", plugin.pluginId)
        assertEquals("微博", plugin.displayName)

        val toolset = plugin.toolSets().single()
        assertEquals(
            listOf(
                "weibo_credential_status",
                "weibo_hot_search",
                "weibo_search",
                "weibo_status",
                "weibo_crowd_topics",
                "weibo_crowd_timeline",
                "weibo_crowd_post",
                "weibo_crowd_comment",
                "weibo_crowd_comment_reply",
                "weibo_crowd_comments",
                "weibo_crowd_child_comments",
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
        assertTrue(instructions.contains("<weibo>"))
        assertTrue(instructions.contains("weibo_credential_status"))
        assertTrue(instructions.contains("weibo_crowd_comment_reply"))
        assertTrue(instructions.contains("App ID"))
        assertTrue(instructions.contains("微博龙虾助手"))
    }

    @Test
    fun configureStoresCredentialsAndFallsBackToDefaultBaseUrlOnBlank() {
        val plugin = WeiboPlugin()

        plugin.configure(
            mapOf(
                WeiboPlugin.KEY_APP_ID to "client-id",
                WeiboPlugin.KEY_APP_SECRET to "client-secret",
                WeiboPlugin.KEY_BASE_URL to "",
            ),
        )
        val api = (plugin.toolSets().single() as WeiboToolset)
        assertEquals("client-id", api.api.appId)
        assertEquals("client-secret", api.api.appSecret)
        assertEquals(WeiboApi.DEFAULT_BASE_URL, api.api.baseUrl)
        assertTrue(api.api.hasCredentials())

        plugin.configure(
            mapOf(
                WeiboPlugin.KEY_APP_ID to "",
                WeiboPlugin.KEY_APP_SECRET to "",
                WeiboPlugin.KEY_BASE_URL to "https://weibo.example.com",
            ),
        )
        assertEquals("", api.api.appId)
        assertEquals("", api.api.appSecret)
        assertEquals("https://weibo.example.com", api.api.baseUrl)
        assertTrue(!api.api.hasCredentials())
    }

    @Test
    fun configDeclaresAllExpectedFields() {
        val plugin = WeiboPlugin()
        val keys = plugin.config.fields.map { it.key }.toSet()
        assertEquals(
            setOf(
                WeiboPlugin.KEY_BASE_URL,
                WeiboPlugin.KEY_APP_ID,
                WeiboPlugin.KEY_APP_SECRET,
            ),
            keys,
        )
        val secretField = plugin.config.fields.first { it.key == WeiboPlugin.KEY_APP_SECRET }
        assertTrue(secretField is github.ponyhuang.gimi.pluginapi.PluginConfigField.Text)
        assertTrue((secretField as github.ponyhuang.gimi.pluginapi.PluginConfigField.Text).secret)
    }

    @Test
    fun toolSetInstructionsReferenceIdStringGuidance() {
        val instructions = WEIBO_INSTRUCTIONS
        // 雪花 id 字符串化的约束写在指令里，避免模型以 number 传入丢精度。
        assertNotNull(instructions)
        assertTrue(instructions.contains("2^53"))
        assertTrue(instructions.contains("cid"))
    }
}