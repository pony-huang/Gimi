package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.tools.mcp.McpTransportConfig
import com.google.adk.kt.tools.mcp.RemoteMcpToolsetConfig
import github.ponyhuang.gimi.domain.mcp.model.McpServer

/** 用户配置直接映射到官方远端 MCP，显式保留自定义 header 覆盖 bearer token 的顺序。 */
internal fun McpServer.toOfficialMcpConfig(): RemoteMcpToolsetConfig {
    val requestHeaders = buildMap {
        bearerToken.takeIf(String::isNotBlank)?.let { put("Authorization", "Bearer $it") }
        headers.lineSequence().map(String::trim).filter { '=' in it }.forEach { line ->
            val (name, value) = line.split('=', limit = 2)
            if (name.isNotBlank()) {
                keys.firstOrNull { it.equals(name.trim(), ignoreCase = true) }?.let(::remove)
                put(name.trim(), value.trim())
            }
        }
    }
    val endpoint = endpointUrl.trim()
    return RemoteMcpToolsetConfig(
        transport = McpTransportConfig.StreamableHttp(
            url = endpoint,
            headers = requestHeaders,
            allowInsecureHttp = endpoint.startsWith("http://", ignoreCase = true),
        ),
        useMcpResources = true,
    )
}

internal fun McpServer.toMcpToolset(): Toolset = toOfficialMcpConfig().toToolset()
