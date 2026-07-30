package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelServicesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateModelServiceUseCase
import github.ponyhuang.gimi.feature.modelsettings.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LLMModelServiceListViewModel @Inject constructor(
    observeServices: ObserveModelServicesUseCase,
    observeLoadState: ObserveModelCatalogLoadStateUseCase,
    private val updateModelService: UpdateModelServiceUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")

    // 缓冲若干条一次性反馈，避免 Route 尚未开始收集时丢失。
    private val _effects = MutableSharedFlow<ModelServiceListEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    val uiState = combine(
        observeServices(),
        observeLoadState(),
        query,
        runWhenAgentIdle.state,
    ) { services, loadState, currentQuery, runtimeState ->
        ModelServiceListUiState(
            loadState = loadState,
            query = currentQuery,
            items = if (currentQuery.isBlank()) {
                services
            } else {
                services.filter { service ->
                    service.id.contains(currentQuery, ignoreCase = true) ||
                        service.name.contains(currentQuery, ignoreCase = true)
                }
            },
            isMutationBlocked = runtimeState.isBusy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModelServiceListUiState(),
    )

    fun onAction(action: ModelServiceListAction) {
        when (action) {
            is ModelServiceListAction.QueryChanged -> query.value = action.value
            is ModelServiceListAction.EnabledChanged ->
                viewModelScope.launch {
                    when (runWhenAgentIdle {
                        updateModelService.enabled(action.serviceId, action.enabled)
                    }) {
                        is AgentMutationResult.Applied -> Unit
                        AgentMutationResult.BlockedByActiveAgent -> _effects.emit(
                            ModelServiceListEffect.ShowToast(
                                R.string.modelsettings_agent_mutation_blocked,
                            ),
                        )
                    }
                }
        }
    }
}
