package github.ponyhuang.gimi.plugin.xiaohongshu

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.pluginapi.PluginConfigAction
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaohongshuPluginTest {

    @Test
    fun pluginExposesReferenceProjectCapabilitiesThroughToolset() = runTest {
        val plugin = XiaohongshuPlugin(injectedService = FakeXiaohongshuService())

        assertEquals("xiaohongshu", plugin.pluginId)
        assertEquals("小红书", plugin.displayName)
        assertTrue(plugin.config.fields.isEmpty())
        assertTrue(plugin.tools().isEmpty())
        assertEquals(16, plugin.toolCount)
        val toolset = plugin.toolSets().single()
        assertEquals(
            listOf(
                PluginConfigAction(id = "login", label = "登录小红书"),
                PluginConfigAction(id = "logout", label = "退出登录"),
            ),
            plugin.config.actions,
        )
        assertEquals(
            listOf(
                "check_login_status",
                "get_login_qrcode",
                "delete_cookies",
                "list_feeds",
                "search_feeds",
                "get_feed_detail",
                "user_profile",
                "post_comment_to_feed",
                "reply_comment_in_feed",
                "like_feed",
                "favorite_feed",
                "get_my_profile",
                "get_unread_count",
                "list_notifications",
                "reply_notification",
                "like_notification",
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
        assertTrue(instructions.contains("<xiaohongshu>"))
        assertTrue(instructions.contains("feed_id"))
        assertTrue(instructions.contains("xsec_token"))
        assertTrue(instructions.contains("notification"))
    }

    @Test
    fun loginUsesWebsitePageStateInsteadOfMcpOrServerAddress() {
        val request = XiaohongshuPlugin(injectedService = FakeXiaohongshuService())
            .configActionBrowserRequest("login")

        assertNotNull(request)
        assertEquals("https://www.xiaohongshu.com/explore", request?.authorizeUrl)
        assertEquals("https://www.xiaohongshu.com", request?.captureCookiesForUrl)
        assertTrue(request?.completionScript.orEmpty().contains(".main-container .user"))
        assertEquals(true, request?.desktopMode)
    }
}

private class FakeXiaohongshuService : XiaohongshuService
