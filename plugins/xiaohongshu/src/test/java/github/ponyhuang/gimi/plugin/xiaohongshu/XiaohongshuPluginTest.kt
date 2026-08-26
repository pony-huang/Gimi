package github.ponyhuang.gimi.plugin.xiaohongshu

import github.ponyhuang.gimi.pluginapi.PluginConfigAction
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaohongshuPluginTest {

    @Test
    fun pluginExposesReferenceProjectCapabilitiesWithoutServerConfiguration() {
        val plugin = XiaohongshuPlugin(injectedService = FakeXiaohongshuService())

        assertEquals("xiaohongshu", plugin.pluginId)
        assertEquals("小红书", plugin.displayName)
        assertTrue(plugin.config.fields.isEmpty())
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
                "publish_content",
                "list_feeds",
                "search_feeds",
                "get_feed_detail",
                "user_profile",
                "post_comment_to_feed",
                "reply_comment_in_feed",
                "publish_with_video",
                "like_feed",
                "favorite_feed",
                "get_my_profile",
                "get_unread_count",
                "list_notifications",
                "reply_notification",
                "like_notification",
            ),
            plugin.tools().map { it.name },
        )
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
