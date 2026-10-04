package github.ponyhuang.gimi.domain.mcp.model

import kotlinx.serialization.json.JsonObject

/**
 * 服务器声明的工具及原始入参定义，供详情展示使用，不经过模型供应商的 schema 转换。
 * @property inputSchema 工具原始 JSON Schema；未提供时为空。
 */
data class McpToolSummary(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject? = null,
)

/**
 * MCP 服务器连通性探测结果。[reachable] 为 false 时通过 [errorMessage] 给出可读原因。
 */
data class McpProbeResult(
    val reachable: Boolean,
    val serverName: String? = null,
    val serverVersion: String? = null,
    val tools: List<McpToolSummary> = emptyList(),
    val resources: List<String> = emptyList(),
    val prompts: List<String> = emptyList(),
    val errorMessage: String? = null,
)
