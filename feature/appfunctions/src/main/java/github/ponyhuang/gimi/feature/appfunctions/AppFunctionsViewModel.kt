package github.ponyhuang.gimi.feature.appfunctions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.appfunctions.repository.AppFunctionRepository
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** AppFunctions 两级设置页共享的状态与授权操作。 */
@HiltViewModel
class AppFunctionsViewModel @Inject constructor(
    repository: AppFunctionRepository,
    private val setSelection: SetAppFunctionsSelectionUseCase,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalState())
    private val _effects = MutableSharedFlow<AppFunctionsEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<AppFunctionsEffect> = _effects.asSharedFlow()

    val uiState = combine(
        repository.state,
        setSelection.agentRuntimeState,
        localState,
    ) { catalog, runtime, local ->
        AppFunctionsUiState(
            catalog = catalog,
            isMutationBlocked = runtime.isBusy,
            query = local.query,
            filter = local.filter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppFunctionsUiState(catalog = repository.state.value),
    )

    fun onAction(action: AppFunctionsAction) {
        when (action) {
            is AppFunctionsAction.SetFeatureEnabled -> mutate {
                setSelection.setFeatureEnabled(action.enabled)
            }
            is AppFunctionsAction.SetAppEnabled -> mutate {
                setSelection.setAppEnabled(action.packageName, action.enabled)
            }
            is AppFunctionsAction.SetFunctionEnabled -> mutate {
                setSelection.setFunctionEnabled(action.key, action.enabled)
            }
            is AppFunctionsAction.SetQuery -> localState.update { it.copy(query = action.value) }
            is AppFunctionsAction.SetFilter -> localState.update { it.copy(filter = action.value) }
        }
    }

    private fun mutate(block: suspend () -> AppFunctionsMutationResult) {
        viewModelScope.launch {
            when (block()) {
                AppFunctionsMutationResult.APPLIED -> Unit
                AppFunctionsMutationResult.BLOCKED_BY_ACTIVE_AGENT ->
                    _effects.emit(AppFunctionsEffect.AgentBusy)
                AppFunctionsMutationResult.UNAVAILABLE ->
                    _effects.emit(AppFunctionsEffect.FeatureUnavailable)
            }
        }
    }

    /**
     * 页面本地搜索条件。
     *
     * @property query 搜索词。
     * @property filter 状态筛选。
     */
    private data class LocalState(
        val query: String = "",
        val filter: AppFunctionStatusFilter = AppFunctionStatusFilter.ALL,
    )
}
