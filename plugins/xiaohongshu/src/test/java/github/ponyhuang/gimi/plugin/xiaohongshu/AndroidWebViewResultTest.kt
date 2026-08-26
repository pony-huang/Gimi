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
}
