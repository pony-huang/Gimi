package github.ponyhuang.gimi.feature.mcp

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import github.ponyhuang.gimi.domain.mcp.model.McpTransport

/** MCP 各屏共用的本地化文案片段。 */

@Composable
internal fun localizeMcpError(error: String): String = when (error) {
    "mcp.connection_error" -> stringResource(R.string.mcp_connection_error_default)
    "mcp.save_error" -> stringResource(R.string.mcp_save_error_default)
    else -> error
}

@Composable
internal fun McpTransport.displayName(): String = when (this) {
    McpTransport.SSE -> stringResource(R.string.mcp_transport_sse)
    McpTransport.STREAMABLE_HTTP -> stringResource(R.string.mcp_transport_streamable_http)
}
