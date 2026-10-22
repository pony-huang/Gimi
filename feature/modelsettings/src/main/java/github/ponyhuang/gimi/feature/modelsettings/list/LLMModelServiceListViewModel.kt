package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelServicesUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LLMModelServiceListViewModel @Inject constructor(
    observeServices: ObserveModelServicesUseCase,
    observeLoadState: ObserveModelCatalogLoadStateUseCase,
    runWhenAgentIdle: RunWhenAgentIdleUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    val uiState = combine(observeServices(), observeLoadState(), query, runWhenAgentIdle.state) {
        services, loadState, currentQuery, runtime ->
        ModelServiceListUiState(loadState, currentQuery, services, runtime.isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelServiceListUiState())

    fun onAction(action: ModelServiceListAction) {
        when (action) {
            is ModelServiceListAction.QueryChanged -> query.value = action.value
        }
    }
}
