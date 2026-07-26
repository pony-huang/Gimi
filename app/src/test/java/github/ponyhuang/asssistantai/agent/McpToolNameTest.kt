package github.ponyhuang.asssistantai.agent

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolNameTest {
    @Test
    fun namesAreValidatedBoundedAndNamespacedByServer() {
        assertNull(mcpToolName("server", "bad name"))
        assertNull(mcpToolName("server", "x".repeat(129)))
        val first = requireNotNull(mcpToolName("server-a", "search.items"))
        val second = requireNotNull(mcpToolName("server-b", "search.items"))
        assertNotEquals(first, second)
        assertTrue(first.length <= 64)
    }
}
