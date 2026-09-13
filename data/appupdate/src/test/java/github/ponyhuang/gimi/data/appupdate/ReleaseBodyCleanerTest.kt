package github.ponyhuang.gimi.data.appupdate

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseBodyCleanerTest {

    @Test
    fun `null and blank bodies become empty`() {
        assertEquals("", ReleaseBodyCleaner.clean(null))
        assertEquals("", ReleaseBodyCleaner.clean(""))
        assertEquals("", ReleaseBodyCleaner.clean("   \n \n"))
    }

    @Test
    fun `body with only full changelog line becomes empty`() {
        val body = "**Full Changelog**: https://github.com/pony-huang/Gimi/compare/v0.9.5...v0.9.6"
        assertEquals("", ReleaseBodyCleaner.clean(body))
    }

    @Test
    fun `full changelog line is stripped from mixed body`() {
        val body = """
            - feat: 实现聊天轮次活动时间线
            - fix: 修复语音唤醒耗电问题

            **Full Changelog**: https://github.com/pony-huang/Gimi/compare/v0.9.5...v0.9.6
        """.trimIndent()
        val expected = """
            - feat: 实现聊天轮次活动时间线
            - fix: 修复语音唤醒耗电问题
        """.trimIndent()
        assertEquals(expected, ReleaseBodyCleaner.clean(body))
    }

    @Test
    fun `unbolded and case-insensitive full changelog variants are stripped`() {
        assertEquals("", ReleaseBodyCleaner.clean("Full changelog: https://example.com"))
        assertEquals("", ReleaseBodyCleaner.clean("  **Full Changelog**: link"))
    }

    @Test
    fun `leading blank lines and consecutive blanks are collapsed`() {
        val body = "\n\n- feat: a\n\n\n- fix: b\n\n"
        assertEquals("- feat: a\n\n- fix: b", ReleaseBodyCleaner.clean(body))
    }

    @Test
    fun `other markdown content is preserved as-is`() {
        val body = "## What's Changed\n\n* feat: x by @pony"
        assertEquals(body, ReleaseBodyCleaner.clean(body))
    }
}
