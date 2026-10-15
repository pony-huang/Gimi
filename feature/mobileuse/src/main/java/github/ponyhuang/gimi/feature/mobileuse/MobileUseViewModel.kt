package github.ponyhuang.gimi.feature.mobileuse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 暴露 Shizuku 开关与当前状态；权限对话框由 Route 的用户操作触发。 */
@HiltViewModel
class MobileUseViewModel @Inject constructor(
    private val repository: MobileUseRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(currentState())
    val uiState: StateFlow<MobileUseUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch { repository.enabled.collect { refresh() } }
    }

    fun refresh() {
        // 保留进行中的保存标记：开关请求由 setEnabled 独占，refresh 只负责刷新仓库读数。
        mutableUiState.update { currentState().copy(updating = it.updating) }
    }

    /** 保存开关；关闭时仓库释放后台任务，页面随即收起状态与操作区。 */
    fun setEnabled(enabled: Boolean) {
        if (mutableUiState.value.updating) return
        mutableUiState.update { it.copy(updating = true) }
        viewModelScope.launch {
            try {
                repository.setEnabled(enabled)
            } finally {
                mutableUiState.value = currentState()
            }
        }
    }

    fun requestPermission() {
        if (uiState.value.availability == MobileUseAvailability.PERMISSION_REQUIRED) {
            repository.requestPermission(REQUEST_CODE)
        }
        refresh()
    }

    private fun currentState(): MobileUseUiState {
        val availability = repository.availability()
        return MobileUseUiState(
            availability = availability,
            textInputAvailable = availability != MobileUseAvailability.DISABLED && repository.textInputAvailable(),
            enabled = repository.enabled.value,
        )
    }

    private companion object {
        const val REQUEST_CODE = 2701
    }
}
