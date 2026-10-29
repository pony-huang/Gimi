package github.ponyhuang.gimi.feature.a2a

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentCardSnapshot
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentProbeResult
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentConnectionTester
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentRepository
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
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
class A2aSettingsViewModel @Inject constructor(
    private val repository: RemoteAgentRepository,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val testConnection: RemoteAgentConnectionTester,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalState())

    private val _effects = MutableSharedFlow<A2aSettingsEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    // 仓库接口即 feature↔data 的契约边界；配置读写不再包一层透传 use case。
    val uiState = combine(repository.observeAgents(), localState, runWhenAgentIdle.state) {
            agents, local, runtimeState ->
        A2aSettingsUiState(
            agents = agents,
            editor = local.editor,
            editorCard = local.editorCardSnapshot,
            isMutationBlocked = runtimeState.isBusy,
            isTestingConnection = local.isTestingConnection,
            connectionError = local.connectionError,
            menuServerId = local.menuAgentId?.takeIf { id -> agents.any { it.id == id } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = A2aSettingsUiState(),
    )

    fun onAction(action: A2aSettingsAction) {
        when (action) {
            is A2aSettingsAction.ToggleAgent ->
                mutate { repository.save(action.agent.copy(isEnabled = action.enabled)) }
            is A2aSettingsAction.AgentMenuChanged -> localState.update {
                it.copy(menuAgentId = action.agentId)
            }
            is A2aSettingsAction.LoadEditor -> loadEditor(action.agentId)
            is A2aSettingsAction.EditorChanged -> localState.update {
                it.copy(editor = action.draft, connectionError = null)
            }
            A2aSettingsAction.TestConnection -> testConnection()
            A2aSettingsAction.SaveEditor -> saveEditor()
            A2aSettingsAction.DeleteEditor -> deleteEditor()
        }
    }

    private fun loadEditor(agentId: String?) {
        val current = localState.value.editor
        if (current != null && (agentId == current.id || agentId == null && current.isNew)) return
        val agent = agentId?.let(repository::agent) ?: RemoteAgentConfig()
        localState.update {
            it.copy(
                editor = agent.toDraft(isNew = agentId == null),
                editorCardSnapshot = agent.cardSnapshot,
                connectionError = null,
            )
        }
    }

    /**
     * 保存前先实时探测连通性：卡片不可达则停留在编辑器并展示错误，严格阻止保存；
     * 探测成功时把最新卡片快照随配置一并持久化。探测是只读网络调用，
     * 不经过 [runWhenAgentIdle] 的 agent 忙碌门控。
     */
    private fun saveEditor() {
        val draft = localState.value.editor ?: return
        if (draft.name.isBlank() || draft.endpointUrl.isBlank()) return
        if (localState.value.isTestingConnection) return
        viewModelScope.launch {
            localState.update { it.copy(isTestingConnection = true, connectionError = null) }
            val result = probeSafely { testConnection.test(draft.toAgent()) }
            if (result.reachable) {
                try {
                    runWhenAgentIdle {
                        repository.save(draft.toAgent().copy(cardSnapshot = result.card))
                        localState.update { it.copy(isTestingConnection = false) }
                        _effects.tryEmit(A2aSettingsEffect.Saved)
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

    /** 编辑器内的主动测试：成功后立即用最新卡片快照刷新详情区，失败展示错误。 */
    private fun testConnection() {
        val draft = localState.value.editor ?: return
        if (draft.endpointUrl.isBlank()) return
        if (localState.value.isTestingConnection) return
        viewModelScope.launch {
            localState.update { it.copy(isTestingConnection = true, connectionError = null) }
            val result = probeSafely { testConnection.test(draft.toAgent()) }
            localState.update {
                if (result.reachable) {
                    it.copy(isTestingConnection = false, editorCardSnapshot = result.card)
                } else {
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
            repository.delete(draft.id)
            _effects.tryEmit(A2aSettingsEffect.Close)
        }
    }

    /** 远程 agent 属于外部边界；任何非取消异常都转为可展示结果，避免逃出 ViewModel 协程。 */
    private suspend fun probeSafely(block: suspend () -> RemoteAgentProbeResult?): RemoteAgentProbeResult =
        try {
            block() ?: RemoteAgentProbeResult(reachable = false, errorMessage = DEFAULT_CONNECTION_ERROR)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RemoteAgentProbeResult(reachable = false, errorMessage = DEFAULT_CONNECTION_ERROR)
        }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch { runWhenAgentIdle { block() } }
    }

    /** 编辑器与配置菜单的本地交互状态。 */
    private data class LocalState(
        val editor: RemoteAgentEditorDraft? = null,
        val editorCardSnapshot: RemoteAgentCardSnapshot? = null,
        val isTestingConnection: Boolean = false,
        val connectionError: String? = null,
        val menuAgentId: String? = null,
    )

    private companion object {
        const val DEFAULT_CONNECTION_ERROR = "a2a.connection_error"
        const val DEFAULT_SAVE_ERROR = "a2a.save_error"
    }
}
