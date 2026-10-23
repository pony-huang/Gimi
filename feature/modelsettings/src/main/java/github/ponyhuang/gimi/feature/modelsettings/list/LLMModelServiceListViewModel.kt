package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelServicesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SetModelServiceEnabledUseCase
import github.ponyhuang.gimi.feature.modelsettings.R
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LLMModelServiceListViewModel @Inject constructor(
    observeServices: ObserveModelServicesUseCase,
    observeLoadState: ObserveModelCatalogLoadStateUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val setServiceEnabled: SetModelServiceEnabledUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val mutation = MutableStateFlow(ListMutationState())
    val uiState = combine(observeServices(), observeLoadState(), query, runWhenAgentIdle.state, mutation) {
        services, loadState, currentQuery, runtime, operation ->
        ModelServiceListUiState(loadState, currentQuery, services, runtime.isBusy, operation.pendingIds, operation.errorRes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelServiceListUiState())

    fun onAction(action: ModelServiceListAction) {
        when (action) {
            is ModelServiceListAction.QueryChanged -> query.value = action.value
            is ModelServiceListAction.EnabledChanged -> changeEnabled(action.serviceId, action.enabled)
        }
    }

    private fun changeEnabled(id: String, enabled: Boolean) {
        val service = uiState.value.items.firstOrNull { it.id == id && it.isAdded } ?: return
        if (runWhenAgentIdle.state.value.isBusy || id in mutation.value.pendingIds || service.isEnabled == enabled) return
        // 同步占位防止连续点击；开关跟随仓库回流，失败不会留下乐观更新的错误状态。
        mutation.update { it.copy(pendingIds = it.pendingIds + id, errorRes = null) }
        viewModelScope.launch {
            try {
                val error = when (val result = runWhenAgentIdle { setServiceEnabled(id, enabled) }) {
                    AgentMutationResult.BlockedByActiveAgent -> R.string.modelsettings_agent_mutation_blocked
                    is AgentMutationResult.Applied -> if (result.value) null else R.string.modelsettings_write_failed
                }
                mutation.update { it.copy(errorRes = error) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutation.update { it.copy(errorRes = R.string.modelsettings_write_failed) }
            } finally {
                mutation.update { it.copy(pendingIds = it.pendingIds - id) }
            }
        }
    }
}

/** 列表即时写入状态；独立于仓库快照以避免覆盖连接与模型。 */
private data class ListMutationState(val pendingIds: Set<String> = emptySet(), val errorRes: Int? = null)
