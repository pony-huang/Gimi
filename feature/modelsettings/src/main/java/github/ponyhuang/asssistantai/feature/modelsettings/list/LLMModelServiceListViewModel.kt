package github.ponyhuang.asssistantai.feature.modelsettings.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveModelServicesUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.UpdateModelServiceUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LLMModelServiceListViewModel @Inject constructor(
    observeServices: ObserveModelServicesUseCase,
    observeLoadState: ObserveModelCatalogLoadStateUseCase,
    private val updateModelService: UpdateModelServiceUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState = combine(
        observeServices(),
        observeLoadState(),
        query,
    ) { services, loadState, currentQuery ->
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
                updateModelService.enabled(action.serviceId, action.enabled)
        }
    }
}
