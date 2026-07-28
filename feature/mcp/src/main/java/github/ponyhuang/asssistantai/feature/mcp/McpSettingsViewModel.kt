package github.ponyhuang.asssistantai.feature.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.runtime.isBusy
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.usecase.ManageMcpServersUseCase
import github.ponyhuang.asssistantai.domain.mcp.usecase.ObserveMcpServersUseCase
import javax.inject.Inject
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
            is McpSettingsAction.ImportJsonChanged -> localState.update {
                it.copy(importJson = action.value, importResult = null)
            }
            McpSettingsAction.ImportServers -> importServers()
            is McpSettingsAction.LoadEditor -> loadEditor(action.serverId)
            is McpSettingsAction.EditorChanged -> localState.update {
                it.copy(editor = action.draft)
            }
            is McpSettingsAction.TransportMenuChanged -> localState.update {
                it.copy(isTransportMenuExpanded = action.expanded)
            }
            is McpSettingsAction.TransportSelected -> localState.update {
                it.copy(
                    editor = it.editor?.copy(transport = action.transport),
                    isTransportMenuExpanded = false,
                )
            }
            McpSettingsAction.SaveEditor -> saveEditor()
            McpSettingsAction.DeleteEditor -> deleteEditor()
        }
    }

    private fun importServers() {
        mutate {
            val result = manageServers.importJson(localState.value.importJson)
            localState.update { it.copy(importResult = result.message) }
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
            )
        }
    }

    private fun saveEditor() {
        val draft = localState.value.editor ?: return
        if (draft.name.isBlank() || draft.endpointUrl.isBlank()) return
        mutate {
            manageServers.save(draft.toServer())
            _effects.tryEmit(McpSettingsEffect.Close)
        }
    }

    private fun deleteEditor() {
        val draft = localState.value.editor?.takeUnless { it.isNew } ?: return
        mutate {
            manageServers.delete(draft.id)
            _effects.tryEmit(McpSettingsEffect.Close)
        }
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch { runWhenAgentIdle { block() } }
    }

    private data class LocalState(
        val importJson: String = "",
        val importResult: String? = null,
        val editor: McpEditorDraft? = null,
        val isTransportMenuExpanded: Boolean = false,
    )
}
