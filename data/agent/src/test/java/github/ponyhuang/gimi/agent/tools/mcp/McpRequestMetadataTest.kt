package github.ponyhuang.gimi.agent.tools.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class McpRequestMetadataTest {
  @Test
  fun `adds a distinct progress token only when progress is observed`() {
    val disabled = requestMeta(hasProgressConsumers = false)
    val first = requestMeta(hasProgressConsumers = true)
    val second = requestMeta(hasProgressConsumers = true)

    assertNull(disabled)
    assertEquals(setOf("progressToken"), first!!.keys)
    assertNotEquals(first["progressToken"], second!!["progressToken"])
  }
}
