package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.tools.mcp.McpTransportConfig
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import org.junit.Assert.*
import org.junit.Test

/** 官方 MCP 配置需保留用户端点和凭据优先级，并明确开启已有 HTTP 配置。 */
class OfficialMcpMappingTest {
    @Test
    fun headersAndEndpointMapToOfficialTransport() {
        val server = McpServer(endpointUrl = " https://example.com/custom ", bearerToken = "old", headers = "Authorization=Bearer new\nX-Test=yes")
        val transport = server.toOfficialMcpConfig().transport as McpTransportConfig.StreamableHttp
        assertEquals("https://example.com/custom", transport.url)
        assertEquals("Bearer new", transport.headers["Authorization"])
        assertEquals("yes", transport.headers["X-Test"])
        assertFalse(transport.allowInsecureHttp)
    }

    @Test
    fun configuredHttpRemainsExplicitlyAllowed() {
        val transport = McpServer(endpointUrl = "http://localhost:9000/mcp").toOfficialMcpConfig().transport as McpTransportConfig.StreamableHttp
        assertTrue(transport.allowInsecureHttp)
    }
}
