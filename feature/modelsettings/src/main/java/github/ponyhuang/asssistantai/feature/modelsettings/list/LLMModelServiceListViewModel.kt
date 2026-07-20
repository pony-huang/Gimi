package github.ponyhuang.asssistantai.feature.modelsettings.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.isBusy
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveModelServicesUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.UpdateModelServiceUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private val notice = MutableStateFlow<String?>(null)

    val uiState = combine(
        observeServices(),
        observeLoadState(),
        query,
        runWhenAgentIdle.state,
        notice,
    ) { services, loadState, currentQuery, runtimeState, currentNotice ->
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
            notice = currentNotice,
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
                    notice.value = when (runWhenAgentIdle {
                        updateModelService.enabled(action.serviceId, action.enabled)
                    }) {
                        is AgentMutationResult.Applied -> null
                        AgentMutationResult.BlockedByActiveAgent -> BLOCKED_MESSAGE
                    }
                }
        }
    }

    private companion object {
        const val BLOCKED_MESSAGE = ""
    }
}
