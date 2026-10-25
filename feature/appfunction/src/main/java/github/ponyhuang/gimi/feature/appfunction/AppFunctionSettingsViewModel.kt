package github.ponyhuang.gimi.feature.appfunction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.appfunction.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** 列表、详情及短暂操作状态的唯一不可变快照。 */
data class AppFunctionUiState(
    val catalog: AppFunctionCatalog = AppFunctionCatalog(),
    val enabledPackages: Set<String> = emptySet(),
    val isRefreshing: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val expandedFunctions: Set<String> = emptySet(),
)

/** AppFunction 设置交互，不向 ViewModel 传入平台或导航对象。 */
sealed interface AppFunctionAction {
    /** 重新查询系统索引和授权状态。 */
    data object Refresh : AppFunctionAction
    /** 持久化 Gimi 对指定应用的使用开关。 */
    data class SetEnabled(val packageName: String, val enabled: Boolean) : AppFunctionAction
    /** 展开指定应用中的单个功能声明。 */
    data class ToggleFunction(val packageName: String, val functionId: String) : AppFunctionAction
}

/** 将仓库快照与刷新、保存和展开状态合成为界面状态。 */
@HiltViewModel
class AppFunctionSettingsViewModel @Inject constructor(private val repository: AppFunctionRepository) : ViewModel() {
    private val local = MutableStateFlow(AppFunctionUiState())
    val uiState: StateFlow<AppFunctionUiState> = combine(repository.catalog, repository.enabledPackages, local) { catalog, enabled, state ->
        state.copy(catalog = catalog, enabledPackages = enabled)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    private var refreshing = false
    private var saving = false

    fun onAction(action: AppFunctionAction) {
        when (action) {
            AppFunctionAction.Refresh -> if (!refreshing) {
                refreshing = true
                local.update { it.copy(isRefreshing = true) }
                viewModelScope.launch {
                    try { repository.refresh() }
                    finally {
                        refreshing = false
                        local.update { it.copy(isRefreshing = false) }
                    }
                }
            }
            is AppFunctionAction.SetEnabled -> if (!saving) {
                saving = true
                local.update { it.copy(isSaving = true, saveFailed = false) }
                viewModelScope.launch {
                    try {
                        repository.setEnabled(action.packageName, action.enabled)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        local.update { it.copy(saveFailed = true) }
                    } finally {
                        saving = false
                        local.update { it.copy(isSaving = false) }
                    }
                }
            }
            is AppFunctionAction.ToggleFunction -> {
                val key = "${action.packageName}/${action.functionId}"
                local.update { state ->
                    val expanded = state.expandedFunctions
                    state.copy(expandedFunctions = if (key in expanded) expanded - key else expanded + key)
                }
            }
        }
    }
}
