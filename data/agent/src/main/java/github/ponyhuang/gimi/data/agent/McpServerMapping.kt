package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.data.agent.tools.mcp.McpConnectionParameters
import github.ponyhuang.gimi.data.agent.tools.mcp.McpToolset
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpTransport

/**
 * 将领域模型 [McpServer] 映射为 [McpConnectionParameters]。
 *
 * 按 transport 枚举选择 SSE 或 Streamable HTTP 参数，并将 bearer token 和自定义 headers
 * 解析到 headers map 中。
 */
internal fun McpServer.toMcpConnectionParameters(): McpConnectionParameters {
    val headers: Map<String, String> = buildMap {
        bearerToken.takeIf { it.isNotBlank() }?.let {
            put("Authorization", "Bearer $it")
        }
        this@toMcpConnectionParameters.headers.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .forEach { line ->
                val (name, value) = line.split('=', limit = 2)
                if (name.isNotBlank()) put(name.trim(), value.trim())
            }
    }
    return when (transport) {
        McpTransport.STREAMABLE_HTTP -> McpConnectionParameters.StreamableHttp(
            url = endpointUrl.trim(),
            headers = headers,
        )
        McpTransport.SSE -> McpConnectionParameters.Sse(
            url = endpointUrl.trim(),
            sseEndpoint = "",
            headers = headers,
        )
    }
}

/**
 * 将 [McpServer] 映射为 [McpToolset]，通过 [McpToolset.McpToolsetConfig] 构建。
 *
 * Header 是静态的（来自配置），因此不传 `headerProvider`，[McpToolset] 内部会缓存工具列表。
 */
internal fun McpServer.toMcpToolset(): McpToolset {
    val config = when (val params = toMcpConnectionParameters()) {
        is McpConnectionParameters.Sse ->
            McpToolset.McpToolsetConfig(sseConnectionParams = params)
        is McpConnectionParameters.StreamableHttp ->
            McpToolset.McpToolsetConfig(streamableHttpConnectionParams = params)
        else -> error("Unsupported transport: ${params::class.simpleName}")
    }
    return config.toToolset()
}
