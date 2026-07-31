package github.ponyhuang.gimi.feature.mcp

import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpTransport

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

/** 列表展开的服务器能力状态；[Loaded]/[Failed] 携带探测时的配置快照用于失效判断。 */
sealed interface ServerCapabilityState {
    data object Loading : ServerCapabilityState
    data class Loaded(val result: McpProbeResult, val serverSnapshot: McpServer) : ServerCapabilityState
    data class Failed(val message: String, val serverSnapshot: McpServer) : ServerCapabilityState
}

data class McpSettingsUiState(
    val servers: List<McpServer> = emptyList(),
    val importJson: String = "",
    val importResult: String? = null,
    val editor: McpEditorDraft? = null,
    val isTransportMenuExpanded: Boolean = false,
    val isMutationBlocked: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionError: String? = null,
    val expandedServerId: String? = null,
    val capabilities: Map<String, ServerCapabilityState> = emptyMap(),
)

sealed interface McpSettingsAction {
    data class ToggleServer(val server: McpServer, val enabled: Boolean) : McpSettingsAction
    data class ServerCardClicked(val serverId: String) : McpSettingsAction
    data class RefreshCapabilities(val serverId: String) : McpSettingsAction
    data class ImportJsonChanged(val value: String) : McpSettingsAction
    data object ImportServers : McpSettingsAction
    data class LoadEditor(val serverId: String?) : McpSettingsAction
    data class EditorChanged(val draft: McpEditorDraft) : McpSettingsAction
    data class TransportMenuChanged(val expanded: Boolean) : McpSettingsAction
    data class TransportSelected(val transport: McpTransport) : McpSettingsAction
    data object SaveEditor : McpSettingsAction
    data object DeleteEditor : McpSettingsAction
}

/** 一次性 UI 反馈（关闭页面等），由 Route 经 effects 通道消费。 */
sealed interface McpSettingsEffect {
    /** 保存 / 删除 / 导入成功，请求关闭当前页。 */
    data object Close : McpSettingsEffect
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
