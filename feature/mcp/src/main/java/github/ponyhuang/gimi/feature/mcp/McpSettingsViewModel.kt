package github.ponyhuang.gimi.feature.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpImportError
import github.ponyhuang.gimi.domain.mcp.model.McpProbeResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.usecase.FetchMcpServerCapabilitiesUseCase
import github.ponyhuang.gimi.domain.mcp.usecase.ManageMcpServersUseCase
import github.ponyhuang.gimi.domain.mcp.usecase.ObserveMcpServersUseCase
import github.ponyhuang.gimi.domain.mcp.usecase.TestMcpConnectionUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class McpSettingsViewModel @Inject constructor(
    observeServers: ObserveMcpServersUseCase,
    private val manageServers: ManageMcpServersUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val testConnection: TestMcpConnectionUseCase,
    private val fetchCapabilities: FetchMcpServerCapabilitiesUseCase,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalState())

    private val _effects = MutableSharedFlow<McpSettingsEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    val uiState = combine(observeServers(), localState, runWhenAgentIdle.state) {
            servers, local, runtimeState ->
        McpSettingsUiState(
            servers = servers,
            importJson = local.importJson,
            importResult = local.importResult,
            editor = local.editor,
            isTransportMenuExpanded = local.isTransportMenuExpanded,
            isMutationBlocked = runtimeState.isBusy,
            isTestingConnection = local.isTestingConnection,
            connectionError = local.connectionError,
            expandedServerId = local.expandedServerId?.takeIf { id -> servers.any { it.id == id } },
            // 服务器被删除后丢弃其能力缓存。
            capabilities = local.capabilities.filterKeys { id -> servers.any { it.id == id } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = McpSettingsUiState(),
    )

    fun onAction(action: McpSettingsAction) {
        when (action) {
            is McpSettingsAction.ToggleServer ->
                mutate { manageServers.save(action.server.copy(isEnabled = action.enabled)) }
            is McpSettingsAction.ServerCardClicked -> onServerCardClicked(action.serverId)
            is McpSettingsAction.RefreshCapabilities -> refreshCapabilities(action.serverId)
            is McpSettingsAction.ImportJsonChanged -> localState.update {
                it.copy(importJson = action.value, importResult = null)
            }
            McpSettingsAction.ImportServers -> importServers()
            is McpSettingsAction.LoadEditor -> loadEditor(action.serverId)
            is McpSettingsAction.EditorChanged -> localState.update {
                it.copy(editor = action.draft, connectionError = null)
            }
            is McpSettingsAction.TransportMenuChanged -> localState.update {
                it.copy(isTransportMenuExpanded = action.expanded)
            }
            is McpSettingsAction.TransportSelected -> localState.update {
                it.copy(
                    editor = it.editor?.copy(transport = action.transport),
                    isTransportMenuExpanded = false,
                    connectionError = null,
                )
            }
            McpSettingsAction.SaveEditor -> saveEditor()
            McpSettingsAction.DeleteEditor -> deleteEditor()
        }
    }

    private fun importServers() {
        mutate {
            val result = try {
                manageServers.importJson(localState.value.importJson)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                McpImportResult(errorCode = McpImportError.STORAGE_FAILURE)
            }
            localState.update { it.copy(importResult = result) }
            if (result.error == null && result.imported > 0) {
                _effects.tryEmit(McpSettingsEffect.Close)
            }
        }
    }

    private fun loadEditor(serverId: String?) {
        val current = localState.value.editor
        if (current != null && (serverId == current.id || serverId == null && current.isNew)) return
        val server = serverId?.let(manageServers::server) ?: McpServer()
        localState.update {
            it.copy(
                editor = server.toDraft(isNew = serverId == null),
                isTransportMenuExpanded = false,
                connectionError = null,
            )
        }
    }

    /**
     * 保存前先实时探测连通性：不可达则停留在编辑器并展示错误，严格阻止保存。
     * 探测是只读网络调用，不经过 [runWhenAgentIdle] 的 agent 忙碌门控。
     */
    private fun saveEditor() {
        val draft = localState.value.editor ?: return
        if (draft.name.isBlank() || draft.endpointUrl.isBlank()) return
        if (localState.value.isTestingConnection) return
        viewModelScope.launch {
            localState.update { it.copy(isTestingConnection = true, connectionError = null) }
            val result = probeSafely { testConnection(draft.toServer()) }
            if (result.reachable) {
                try {
                    runWhenAgentIdle {
                        manageServers.save(draft.toServer())
                        localState.update { it.copy(isTestingConnection = false) }
                        _effects.tryEmit(McpSettingsEffect.Saved)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    localState.update {
                        it.copy(isTestingConnection = false, connectionError = DEFAULT_SAVE_ERROR)
                    }
                }
            } else {
                localState.update {
                    it.copy(
                        isTestingConnection = false,
                        connectionError = result.errorMessage ?: DEFAULT_CONNECTION_ERROR,
                    )
                }
            }
        }
    }

    private fun deleteEditor() {
        val draft = localState.value.editor?.takeUnless { it.isNew } ?: return
        mutate {
            manageServers.delete(draft.id)
            _effects.tryEmit(McpSettingsEffect.Close)
        }
    }

    private fun onServerCardClicked(serverId: String) {
        val collapsing = localState.value.expandedServerId == serverId
        localState.update { it.copy(expandedServerId = if (collapsing) null else serverId) }
        if (!collapsing) fetchCapabilitiesIfStale(serverId)
    }

    private fun refreshCapabilities(serverId: String) {
        localState.update { it.copy(capabilities = it.capabilities - serverId) }
        fetchCapabilitiesIfStale(serverId)
    }

    /** 无缓存或配置快照已过期时发起探测；Loading 状态天然防止重复请求。 */
    private fun fetchCapabilitiesIfStale(serverId: String) {
        val server = uiState.value.servers.firstOrNull { it.id == serverId } ?: return
        val cached = localState.value.capabilities[serverId]
        val snapshot = when (cached) {
            is ServerCapabilityState.Loaded -> cached.serverSnapshot
            is ServerCapabilityState.Failed -> cached.serverSnapshot
            else -> null
        }
        if (cached is ServerCapabilityState.Loading || snapshot == server) return

        localState.update { it.copy(capabilities = it.capabilities + (serverId to ServerCapabilityState.Loading)) }
        viewModelScope.launch {
            val result = probeSafely { fetchCapabilities(serverId) }
            val next = when {
                result.reachable -> ServerCapabilityState.Loaded(result, server)
                else -> ServerCapabilityState.Failed(
                    result.errorMessage ?: DEFAULT_CONNECTION_ERROR,
                    server,
                )
            }
            localState.update { it.copy(capabilities = it.capabilities + (serverId to next)) }
        }
    }

    /** MCP 属于外部边界；任何非取消异常都转为可展示结果，避免逃出 ViewModel 协程。 */
    private suspend fun probeSafely(block: suspend () -> McpProbeResult?): McpProbeResult = try {
        block() ?: McpProbeResult(reachable = false, errorMessage = DEFAULT_CONNECTION_ERROR)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        McpProbeResult(reachable = false, errorMessage = DEFAULT_CONNECTION_ERROR)
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch { runWhenAgentIdle { block() } }
    }

    private data class LocalState(
        val importJson: String = "",
        val importResult: McpImportResult? = null,
        val editor: McpEditorDraft? = null,
        val isTransportMenuExpanded: Boolean = false,
        val isTestingConnection: Boolean = false,
        val connectionError: String? = null,
        val expandedServerId: String? = null,
        val capabilities: Map<String, ServerCapabilityState> = emptyMap(),
    )

    private companion object {
        const val DEFAULT_CONNECTION_ERROR = "mcp.connection_error"
        const val DEFAULT_SAVE_ERROR = "mcp.save_error"
    }
}
