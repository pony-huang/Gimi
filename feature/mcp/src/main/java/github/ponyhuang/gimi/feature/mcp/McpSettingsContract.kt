package github.ponyhuang.gimi.feature.mcp

import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
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

/** 服务器工具详情的能力状态；[Loaded]/[Failed] 携带探测时的配置快照用于失效判断。 */
sealed interface ServerCapabilityState {
    /** 正在读取指定配置的工具声明。 */
    data class Loading(val serverSnapshot: McpServer) : ServerCapabilityState
    /** 已读取的声明及其配置快照，用于识别过期缓存。 */
    data class Loaded(val result: McpProbeResult, val serverSnapshot: McpServer) : ServerCapabilityState
    /** 安全错误信息及失败时的配置快照，可显式重试。 */
    data class Failed(val message: String, val serverSnapshot: McpServer) : ServerCapabilityState
}

/**
 * MCP 配置与独立工具详情页面的不可变状态。
 * @property menuServerId 正在显示操作菜单的服务器 ID。
 * @property toolsServerId 当前查看工具详情的服务器 ID。
 * @property expandedToolNames 当前服务器已展开的工具名称，不触发额外探测。
 */
data class McpSettingsUiState(
    val servers: List<McpServer> = emptyList(),
    val isLoadingServers: Boolean = true,
    val importJson: String = "",
    val importResult: McpImportResult? = null,
    val editor: McpEditorDraft? = null,
    val isTransportMenuExpanded: Boolean = false,
    val isMutationBlocked: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionError: String? = null,
    val menuServerId: String? = null,
    val toolsServerId: String? = null,
    val expandedToolNames: Set<String> = emptySet(),
    val capabilities: Map<String, ServerCapabilityState> = emptyMap(),
)

sealed interface McpSettingsAction {
    data class ToggleServer(val server: McpServer, val enabled: Boolean) : McpSettingsAction
    /** 打开或关闭某个服务器的操作菜单。 */
    data class ServerMenuChanged(val serverId: String?) : McpSettingsAction
    /** 进入指定服务器的工具详情，并按配置快照加载能力。 */
    data class LoadTools(val serverId: String) : McpSettingsAction
    /** 展开或收起当前服务器工具详情，不发起网络请求。 */
    data class ToggleTool(val toolName: String) : McpSettingsAction
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

    /** 保存成功，提示用户后关闭编辑页。 */
    data object Saved : McpSettingsEffect
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
