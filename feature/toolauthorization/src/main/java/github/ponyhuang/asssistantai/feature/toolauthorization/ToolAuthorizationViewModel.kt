package github.ponyhuang.asssistantai.feature.toolauthorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ToolAuthorizationViewModel @Inject constructor(
    private val repository: ToolAuthorizationRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState = combine(repository.tools, query) { tools, currentQuery ->
        ToolAuthorizationUiState(query = currentQuery, tools = tools)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ToolAuthorizationUiState(tools = repository.tools.value),
    )

    fun onAction(action: ToolAuthorizationAction) {
        when (action) {
            is ToolAuthorizationAction.Search -> query.value = action.query
            is ToolAuthorizationAction.SetEnabled -> repository.setEnabled(action.toolId, action.enabled)
            is ToolAuthorizationAction.SetAllEnabled -> repository.setAllEnabled(action.enabled)
        }
    }
}
