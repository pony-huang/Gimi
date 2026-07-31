package github.ponyhuang.gimi.domain.mcp.repository

import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer

/**
 * MCP 服务器连通性探测端口。domain 层不依赖 MCP SDK，由 data 层实现。
 */
interface McpConnectionTester {

    /** 对给定的（可能尚未保存的）服务器配置做实时探测，永不抛异常。 */
    suspend fun test(server: McpServer): McpProbeResult
}
