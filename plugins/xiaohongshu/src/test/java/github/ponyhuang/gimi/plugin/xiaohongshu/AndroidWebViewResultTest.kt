package github.ponyhuang.gimi.plugin.xiaohongshu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidWebViewResultTest {

    @Test
    fun decodesEvaluateJavascriptStringWithoutCorruptingNestedJson() {
        assertEquals(
            """[{"id":"feed-1"}]""",
            decodeWebViewResult("\"[{\\\"id\\\":\\\"feed-1\\\"}]\""),
        )
        assertEquals("true", decodeWebViewResult("true"))
        assertNull(decodeWebViewResult("null"))
    }

    @Test
    fun upgradesCleartextOnlyOnKnownXiaohongshuHosts() {
        // 已知域名：http -> https（修复 ERR_CLEARTEXT_NOT_PERMITTED）
        assertEquals(
            "https://www.xiaohongshu.com/search_result?keyword=%E5%B9%BF%E5%B7%9E",
            upgradeToHttps("http://www.xiaohongshu.com/search_result?keyword=%E5%B9%BF%E5%B7%9E"),
        )
        assertEquals("https://xhscdn.com/a.js", upgradeToHttps("http://xhscdn.com/a.js"))
        assertEquals("https://s.xhslink.com/x", upgradeToHttps("http://s.xhslink.com/x"))
        // 已是 https：原样返回
        assertEquals(
            "https://www.xiaohongshu.com/explore",
            upgradeToHttps("https://www.xiaohongshu.com/explore"),
        )
        // 非已知域名：不盲目升级
        assertEquals("http://example.com/x", upgradeToHttps("http://example.com/x"))
    }
}
