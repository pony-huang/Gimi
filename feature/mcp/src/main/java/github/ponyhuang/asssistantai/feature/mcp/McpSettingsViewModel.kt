package github.ponyhuang.asssistantai.feature.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.usecase.ManageMcpServersUseCase
import github.ponyhuang.asssistantai.domain.mcp.usecase.ObserveMcpServersUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class McpSettingsViewModel @Inject constructor(
    observeServers: ObserveMcpServersUseCase,
    private val manageServers: ManageMcpServersUseCase,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalState())

    val uiState = combine(observeServers(), localState) { servers, local ->
        McpSettingsUiState(
            servers = servers,
            importJson = local.importJson,
            importResult = local.importResult,
            editor = local.editor,
            isTransportMenuExpanded = local.isTransportMenuExpanded,
            shouldClose = local.shouldClose,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = McpSettingsUiState(),
    )

    fun onAction(action: McpSettingsAction) {
        when (action) {
            is McpSettingsAction.ToggleServer ->
                manageServers.save(action.server.copy(isEnabled = action.enabled))
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
            McpSettingsAction.CloseConsumed -> localState.update { it.copy(shouldClose = false) }
        }
    }

    private fun importServers() {
        val result = manageServers.importJson(localState.value.importJson)
        localState.update {
            it.copy(
                importResult = result.message,
                shouldClose = result.error == null && result.imported > 0,
            )
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
        manageServers.save(draft.toServer())
        localState.update { it.copy(shouldClose = true) }
    }

    private fun deleteEditor() {
        val draft = localState.value.editor?.takeUnless { it.isNew } ?: return
        manageServers.delete(draft.id)
        localState.update { it.copy(shouldClose = true) }
    }

    private data class LocalState(
        val importJson: String = "",
        val importResult: String? = null,
        val editor: McpEditorDraft? = null,
        val isTransportMenuExpanded: Boolean = false,
        val shouldClose: Boolean = false,
    )
}
