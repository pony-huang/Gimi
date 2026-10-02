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
import kotlinx.coroutines.launch

/** 仅暴露 Shizuku 当前状态，权限对话框由 Route 的用户操作触发。 */
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
        mutableUiState.value = currentState()
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
        )
    }

    private companion object {
        const val REQUEST_CODE = 2701
    }
}
