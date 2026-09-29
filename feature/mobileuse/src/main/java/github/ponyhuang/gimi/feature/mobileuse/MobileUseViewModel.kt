package github.ponyhuang.gimi.feature.mobileuse

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 仅暴露 Shizuku 当前状态，权限对话框由 Route 的用户操作触发。 */
@HiltViewModel
class MobileUseViewModel @Inject constructor(
    private val repository: MobileUseRepository,
) : ViewModel() {
    private val mutableAvailability = MutableStateFlow(repository.availability())
    val availability: StateFlow<MobileUseAvailability> = mutableAvailability
    private val mutableTextInputAvailable = MutableStateFlow(repository.textInputAvailable())
    val textInputAvailable: StateFlow<Boolean> = mutableTextInputAvailable

    fun refresh() {
        mutableAvailability.value = repository.availability()
        mutableTextInputAvailable.value = repository.textInputAvailable()
    }

    fun requestPermission() {
        repository.requestPermission(REQUEST_CODE)
        refresh()
    }

    private companion object {
        const val REQUEST_CODE = 2701
    }
}
