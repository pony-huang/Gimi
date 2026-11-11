package github.ponyhuang.gimi.plugin.weibo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeiboPluginTest {

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

        // 官方域可用
        plugin.configure(
            mapOf(
                WeiboPlugin.KEY_APP_ID to "client-id",
                WeiboPlugin.KEY_APP_SECRET to "client-secret",
                WeiboPlugin.KEY_BASE_URL to "https://open-im.api.weibo.com",
            ),
        )
        assertEquals("client-id", api.api.appId)
        assertEquals("client-secret", api.api.appSecret)
        assertEquals("https://open-im.api.weibo.com", api.api.baseUrl)

        // 非官方域回退默认：App Secret 只发往官方白名单主机
        plugin.configure(
            mapOf(
                WeiboPlugin.KEY_APP_ID to "",
                WeiboPlugin.KEY_APP_SECRET to "",
                WeiboPlugin.KEY_BASE_URL to "https://weibo.example.com",
            ),
        )
        assertEquals("", api.api.appId)
        assertEquals("", api.api.appSecret)
        assertEquals(WeiboApi.DEFAULT_BASE_URL, api.api.baseUrl)
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

}