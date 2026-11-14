package github.ponyhuang.gimi.feature.modelsettings.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelTemplatesUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 添加目录状态：模板目录与搜索词。 */
data class AddServiceUiState(
    val query: String = "",
    val templates: List<ModelServiceTemplate> = emptyList(),
)

sealed interface AddServiceAction {
    /** 搜索模板名称。 */
    data class QueryChanged(val value: String) : AddServiceAction
}

@HiltViewModel
class AddServiceViewModel @Inject constructor(
    observeTemplates: ObserveModelTemplatesUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    val uiState = combine(observeTemplates(), query) { templates, currentQuery ->
        AddServiceUiState(currentQuery, templates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddServiceUiState())

    fun onAction(action: AddServiceAction) {
        when (action) {
            is AddServiceAction.QueryChanged -> query.value = action.value
        }
    }
}
