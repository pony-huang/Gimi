package github.ponyhuang.asssistantai.domain.speech.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidateWakeKeywordTest {

    @Test
    fun `chinese keyword with valid length passes`() {
        assertNull(validateWakeKeyword("你好助手", "zh-CN"))
        assertNull(validateWakeKeyword("OK", "zh-CN"))
        assertNull(validateWakeKeyword("这".repeat(20), "zh-CN"))
    }

    @Test
    fun `chinese keyword is trimmed before validation`() {
        assertNull(validateWakeKeyword("  你好助手  ", "zh-CN"))
    }

    @Test
    fun `chinese keyword too short or too long fails with InvalidLength`() {
        assertEquals(WakeKeywordError.InvalidLength, validateWakeKeyword("好", "zh-CN"))
        assertEquals(WakeKeywordError.InvalidLength, validateWakeKeyword("", "zh-CN"))
        assertEquals(
            WakeKeywordError.InvalidLength,
            validateWakeKeyword("这".repeat(21), "zh-CN"),
        )
    }

    @Test
    fun `chinese keyword with control characters fails with InvalidCharacters`() {
        assertEquals(WakeKeywordError.InvalidCharacters, validateWakeKeyword("你好\u0001助手", "zh-CN"))
    }

    @Test
    fun `english keyword with two to four lowercase words passes`() {
        assertNull(validateWakeKeyword("hey assistant", "en-US"))
        assertNull(validateWakeKeyword("ok computer now", "en-US"))
        assertNull(validateWakeKeyword("hey don't stop now", "en-US"))
    }

    @Test
    fun `english keyword with wrong word count fails with InvalidWordFormat`() {
        assertEquals(WakeKeywordError.InvalidWordFormat, validateWakeKeyword("hey", "en-US"))
        assertEquals(
            WakeKeywordError.InvalidWordFormat,
            validateWakeKeyword("one two three four five", "en-US"),
        )
    }

    @Test
    fun `english keyword with invalid characters fails with InvalidWordFormat`() {
        assertEquals(WakeKeywordError.InvalidWordFormat, validateWakeKeyword("Hey assistant", "en-US"))
        assertEquals(WakeKeywordError.InvalidWordFormat, validateWakeKeyword("hey 助手", "en-US"))
        assertEquals(WakeKeywordError.InvalidWordFormat, validateWakeKeyword("hey  assistant", "en-US"))
        assertEquals(WakeKeywordError.InvalidWordFormat, validateWakeKeyword("hey-assistant", "en-US"))
    }

    @Test
    fun `english keyword exceeding total length fails with InvalidLength`() {
        assertEquals(
            WakeKeywordError.InvalidLength,
            validateWakeKeyword("a".repeat(41), "en-US"),
        )
    }
}
