package github.ponyhuang.gimi.data.agent.tools.mcp

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class McpRequestMetadataTest {
  @Test
  fun `adds a progress callback only when progress is observed`() {
    val params = McpConnectionParameters.StreamableHttp("https://example.test", readTimeout = 9.seconds)
    val disabled = McpSessionManager(params).requestOptions()
    val enabled = McpSessionManager(params, progressConsumers = listOf({})).requestOptions()

    assertNull(disabled.onProgress)
    assertNotNull(enabled.onProgress)
  }
}
