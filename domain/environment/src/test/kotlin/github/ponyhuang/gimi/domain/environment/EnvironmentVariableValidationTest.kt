package github.ponyhuang.gimi.domain.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 环境变量持久配置与每次执行共用的校验规则。 */
class EnvironmentVariableValidationTest {

    @Test
    fun acceptsEmptyConfigurationAndWellFormedEntries() {
        assertTrue(validEnvironmentVariables(emptyMap()))
        assertTrue(validEnvironmentVariables(mapOf("PATH" to "/usr/bin")))
        assertTrue(validEnvironmentVariables(mapOf("_PRIVATE" to "")))
        // 允许空值与任意内容（换行、空格），执行层负责按字面传递。
        assertTrue(validEnvironmentVariables(mapOf("a1_b2" to "line1\nline2 with spaces")))
    }

    @Test
    fun rejectsMalformedVariableNames() {
        // 数字开头、连字符、空名、内嵌空格都不是合法 shell 标识符。
        assertFalse(validEnvironmentVariables(mapOf("1A" to "v")))
        assertFalse(validEnvironmentVariables(mapOf("A-B" to "v")))
        assertFalse(validEnvironmentVariables(mapOf("" to "v")))
        assertFalse(validEnvironmentVariables(mapOf("A B" to "v")))
    }

    @Test
    fun rejectsValuesContainingNul() {
        // NUL 会截断 C 字符串，进程环境注入前必须拒绝。
        assertFalse(validEnvironmentVariables(mapOf("OK_NAME" to "a\u0000b")))
    }

    @Test
    fun rejectsAsSoonAsAnyEntryIsInvalid() {
        val values = linkedMapOf("GOOD" to "v", "BAD NAME" to "v")
        assertFalse(validEnvironmentVariables(values))
    }
}
