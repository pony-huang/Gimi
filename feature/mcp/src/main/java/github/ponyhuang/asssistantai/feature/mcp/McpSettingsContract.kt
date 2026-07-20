package github.ponyhuang.asssistantai.feature.mcp

import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport

data class McpEditorDraft(
    val id: String,
    val isNew: Boolean,
    val name: String,
    val description: String,
    val endpointUrl: String,
    val transport: McpTransport,
    val bearerToken: String,
    val headers: String,
    val isEnabled: Boolean,
)

data class McpSettingsUiState(
    val servers: List<McpServer> = emptyList(),
    val importJson: String = "",
    val importResult: String? = null,
    val editor: McpEditorDraft? = null,
    val isTransportMenuExpanded: Boolean = false,
    val shouldClose: Boolean = false,
    val isMutationBlocked: Boolean = false,
    val notice: String? = null,
)

sealed interface McpSettingsAction {
    data class ToggleServer(val server: McpServer, val enabled: Boolean) : McpSettingsAction
    data class ImportJsonChanged(val value: String) : McpSettingsAction
    data object ImportServers : McpSettingsAction
    data class LoadEditor(val serverId: String?) : McpSettingsAction
    data class EditorChanged(val draft: McpEditorDraft) : McpSettingsAction
    data class TransportMenuChanged(val expanded: Boolean) : McpSettingsAction
    data class TransportSelected(val transport: McpTransport) : McpSettingsAction
    data object SaveEditor : McpSettingsAction
    data object DeleteEditor : McpSettingsAction
    data object CloseConsumed : McpSettingsAction
}

internal fun McpServer.toDraft(isNew: Boolean) = McpEditorDraft(
    id = id,
    isNew = isNew,
    name = name,
    description = description,
    endpointUrl = endpointUrl,
    transport = transport,
    bearerToken = bearerToken,
    headers = headers,
    isEnabled = isEnabled,
)

internal fun McpEditorDraft.toServer() = McpServer(
    id = id,
    name = name.trim(),
    description = description.trim(),
    endpointUrl = endpointUrl.trim(),
    transport = transport,
    bearerToken = bearerToken.trim(),
    headers = headers.trim(),
    isEnabled = isEnabled,
)
