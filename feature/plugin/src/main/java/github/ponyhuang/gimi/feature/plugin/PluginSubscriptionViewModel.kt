package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.repository.PluginSubscriptionStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 插件订阅源编辑页：追加多个 HTTPS 地址、移除单个来源或恢复官方默认源。
 *
 * 保存成功后列表页通过 [PluginSubscriptionStore.urls] 的变化自动重新拉取目录。
 */
@HiltViewModel
class PluginSubscriptionViewModel @Inject constructor(
    private val store: PluginSubscriptionStore,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<PluginSubscriptionUiState> = combine(
        store.urls,
        local,
    ) { urls, localState ->
        PluginSubscriptionUiState(
            urls = urls,
            defaultUrl = store.defaultUrl,
            isAddDialogVisible = localState.isAddDialogVisible,
            draftUrl = localState.draftUrl,
            error = localState.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PluginSubscriptionUiState(
            urls = store.urls.value,
            defaultUrl = store.defaultUrl,
        ),
    )

    fun onAction(action: PluginSubscriptionAction) {
        when (action) {
            PluginSubscriptionAction.AddClicked -> local.update {
                it.copy(isAddDialogVisible = true, draftUrl = "", error = null)
            }

            PluginSubscriptionAction.DismissAdd -> local.update {
                it.copy(isAddDialogVisible = false, draftUrl = "", error = null)
            }

            is PluginSubscriptionAction.DraftChanged -> local.update {
                it.copy(draftUrl = action.value, error = null)
            }

            PluginSubscriptionAction.ConfirmAdd -> confirmAdd()

            is PluginSubscriptionAction.Remove -> store.remove(action.url)

            PluginSubscriptionAction.Reset -> store.reset()
        }
    }

    private fun confirmAdd() {
        val value = local.value.draftUrl.trim()
        val error = when {
            value.isEmpty() ||
                !value.startsWith("https://", ignoreCase = true) ||
                value.length > MAX_URL_LENGTH ||
                value.any(Char::isWhitespace) -> PluginSubscriptionError.INVALID_URL

            value in store.urls.value -> PluginSubscriptionError.DUPLICATE
            else -> null
        }
        if (error != null) {
            local.update { it.copy(error = error) }
            return
        }
        store.add(value)
        local.update { it.copy(isAddDialogVisible = false, draftUrl = "", error = null) }
    }

    private data class LocalState(
        val isAddDialogVisible: Boolean = false,
        val draftUrl: String = "",
        val error: PluginSubscriptionError? = null,
    )

    private companion object {
        const val MAX_URL_LENGTH = 2_048
    }
}
