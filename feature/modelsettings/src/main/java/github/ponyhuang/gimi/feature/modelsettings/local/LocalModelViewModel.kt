package github.ponyhuang.gimi.feature.modelsettings.local

import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRuntime
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelDownloadStatus
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRepository
import github.ponyhuang.gimi.feature.modelsettings.R
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** 管理下载操作反馈、删除确认及 Agent 空闲门控；文件和任务由 domain 契约负责。 */
@HiltViewModel
class LocalModelViewModel @Inject constructor(
    private val repository: LocalModelRepository,
    private val localRuntime: LocalModelRuntime,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
) : ViewModel() {
    private val expandedModelIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingRemoval = MutableStateFlow<String?>(null)
    private val operating = MutableStateFlow(false)
    private val notice = MutableStateFlow<Int?>(null)
    val uiState = combine(repository.state, pendingRemoval, runWhenAgentIdle.state, operating, notice) { catalog, pending, runtime, busy, message ->
        LocalModelUiState(catalog.loading, catalog.models,
            catalog.models.firstOrNull { it.variant.id == pending }, runtime.isBusy, busy, message)
    }.combine(expandedModelIds) { state, expanded -> state.copy(expandedModelIds = expanded) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalModelUiState())

    fun onAction(action: LocalModelAction) {
        when (action) {
            is LocalModelAction.ToggleDetails -> {
                val current = expandedModelIds.value
                expandedModelIds.value = if (action.id in current) current - action.id else current + action.id
            }
            is LocalModelAction.RequestRemoval -> {
                pendingRemoval.value = repository.state.value.models.firstOrNull {
                    it.variant.id == action.id && it.status == LocalModelDownloadStatus.Ready
                }?.variant?.id
            }
            LocalModelAction.DismissRemoval -> if (!operating.value) pendingRemoval.value = null
            LocalModelAction.ConfirmRemoval -> {
                val id = pendingRemoval.value ?: return
                operate(gated = true) { localRuntime.remove(id); pendingRemoval.value = null }
            }
            is LocalModelAction.Download -> operate { repository.download(action.id) }
            is LocalModelAction.CancelDownload -> operate { repository.cancelDownload(action.id) }
        }
    }

    private fun operate(gated: Boolean = false, block: suspend () -> Unit) {
        if (operating.value) return
        operating.value = true
        notice.value = null
        viewModelScope.launch {
            try {
                if (gated) {
                    when (runWhenAgentIdle { block() }) {
                        is AgentMutationResult.Applied -> Unit
                        AgentMutationResult.BlockedByActiveAgent -> notice.value = R.string.modelsettings_agent_mutation_blocked
                    }
                } else block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                notice.value = R.string.local_model_operation_failed
            } catch (_: IllegalStateException) {
                notice.value = R.string.local_model_operation_failed
            } finally { operating.value = false }
        }
    }
}
