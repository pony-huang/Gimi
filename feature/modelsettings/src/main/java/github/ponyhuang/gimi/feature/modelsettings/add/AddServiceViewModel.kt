package github.ponyhuang.gimi.feature.modelsettings.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelTemplatesUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 添加目录状态：内置服务商模板目录。 */
data class AddServiceUiState(
    val templates: List<ModelServiceTemplate> = emptyList(),
)

@HiltViewModel
class AddServiceViewModel @Inject constructor(
    observeTemplates: ObserveModelTemplatesUseCase,
) : ViewModel() {
    val uiState = observeTemplates()
        .map { templates -> AddServiceUiState(templates) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddServiceUiState())
}
