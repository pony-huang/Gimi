package github.ponyhuang.gimi.feature.mcp

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by MCP server settings. */
sealed interface McpDestination : NavKey {
    /** Configured MCP server list. */
    @Serializable
    data object ServerList : McpDestination

    /** Choice between creating and importing a server. */
    @Serializable
    data object AddOptions : McpDestination

    /** Conversational server import destination. */
    @Serializable
    data object Import : McpDestination

    /**
     * MCP server editor.
     *
     * @property serverId Existing server id, or null when creating one.
     */
    @Serializable
    data class Editor(val serverId: String? = null) : McpDestination
}

/** Resolves all MCP subpages and keeps their internal transitions inside the feature. */
@Composable
fun McpEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    navigate: (NavKey) -> Unit,
    replaceCurrent: (NavKey) -> Unit,
): Boolean = when (destination) {
    McpDestination.ServerList -> {
        McpServerListRoute(
            onBack = onBack,
            onAddServer = { navigate(McpDestination.AddOptions) },
            onNavigateToEditor = { navigate(McpDestination.Editor(it)) },
            onCreateServer = { navigate(McpDestination.Editor()) },
            onImportServers = { navigate(McpDestination.Import) },
        )
        true
    }

    McpDestination.AddOptions -> {
        McpServerAddOptionsRoute(
            onBack = onBack,
            onCreate = { replaceCurrent(McpDestination.Editor()) },
            onImport = { replaceCurrent(McpDestination.Import) },
        )
        true
    }

    McpDestination.Import -> {
        McpServerImportRoute(onBack = onBack)
        true
    }

    is McpDestination.Editor -> {
        McpServerEditorRoute(
            serverId = destination.serverId,
            onBack = onBack,
        )
        true
    }

    else -> false
}
