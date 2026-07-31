package github.ponyhuang.gimi.domain.mcp.repository

import github.ponyhuang.gimi.domain.mcp.model.McpSkippedServer
import kotlinx.coroutines.flow.StateFlow

/**
 * MCP 服务器跳过事件的报告端口：data 层在会话加载工具探测失败时发布，
 * UI 层观察并向用户提示（如 Toast）。domain 不依赖 data 实现。
 */
interface McpSkipReporter {

    /** 最近一次探测中被跳过的服务器集合（每次发布整体替换）。 */
    val skipped: StateFlow<List<McpSkippedServer>>

    fun publish(skipped: List<McpSkippedServer>)
}
