package github.ponyhuang.gimi.plugin.v2ex

import org.junit.Assert.assertEquals
import org.junit.Test

class V2exPluginTest {

    @Test
    fun configureStoresTokenAndFallsBackToDefaultBaseUrlOnBlank() {
        val plugin = V2exPlugin()

        plugin.configure(mapOf(V2exPlugin.KEY_TOKEN to "secret-pat", V2exPlugin.KEY_BASE_URL to ""))
        assertEquals("secret-pat", plugin.apiToken())
        assertEquals(V2exApi.DEFAULT_BASE_URL, plugin.apiBaseUrl())

        // 官方域可用
        plugin.configure(mapOf(V2exPlugin.KEY_BASE_URL to "https://www.v2ex.com/api/v2", V2exPlugin.KEY_TOKEN to ""))
        assertEquals("", plugin.apiToken())
        assertEquals("https://www.v2ex.com/api/v2", plugin.apiBaseUrl())

        // 非官方域回退默认：token 只发往官方白名单主机
        plugin.configure(mapOf(V2exPlugin.KEY_BASE_URL to "https://global.v2ex.co/api/v2", V2exPlugin.KEY_TOKEN to ""))
        assertEquals(V2exApi.DEFAULT_BASE_URL, plugin.apiBaseUrl())
    }
}
