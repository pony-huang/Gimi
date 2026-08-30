package github.ponyhuang.gimi.data.agent.tools.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesToolQueryTest {

    @Test
    fun `splits query on whitespace and deduplicates tokens`() {
        assertEquals(
            listOf("旅行", "照片", "2024"),
            relaxedQueryTokens("  旅行   照片 2024 2024 "),
        )
    }

    @Test
    fun `keeps cjk query as single token when no whitespace exists`() {
        assertEquals(listOf("猫咪照片"), relaxedQueryTokens("猫咪照片"))
    }

    @Test
    fun `blank query yields no tokens`() {
        assertTrue(relaxedQueryTokens("   ").isEmpty())
    }
}
