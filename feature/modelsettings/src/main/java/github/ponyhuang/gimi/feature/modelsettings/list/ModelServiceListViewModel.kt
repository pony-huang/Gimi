package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelInstancesUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ModelServiceListViewModel @Inject constructor(
    observeInstances: ObserveModelInstancesUseCase,
    observeLoadState: ObserveModelCatalogLoadStateUseCase,
) : ViewModel() {
    val uiState = combine(observeInstances(), observeLoadState()) { instances, loadState ->
        ModelServiceListUiState(loadState, instances)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelServiceListUiState())
}
