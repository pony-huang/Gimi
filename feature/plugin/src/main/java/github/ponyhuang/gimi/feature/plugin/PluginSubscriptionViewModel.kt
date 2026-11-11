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
            // 订阅源是可执行内容的来源：https + 合法域名 + 禁止 userinfo（user@host 绕过域名直觉）。
            !isValidSubscriptionUrl(value) -> PluginSubscriptionError.INVALID_URL

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

    /** 订阅地址本地校验：https scheme、可解析主机（域名/IP），无空白、无 userinfo、长度受限。 */
    private fun isValidSubscriptionUrl(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_URL_LENGTH) return false
        if (value.any(Char::isWhitespace)) return false
        if (!value.startsWith("https://", ignoreCase = true)) return false
        // java.net.URI 而非 android.net.Uri：解析逻辑需在 JVM 单测环境可直接验证。
        val uri = try {
            java.net.URI(value)
        } catch (_: java.net.URISyntaxException) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        val host = uri.host ?: return false
        return host.matches(HOST_PATTERN)
    }

    private data class LocalState(
        val isAddDialogVisible: Boolean = false,
        val draftUrl: String = "",
        val error: PluginSubscriptionError? = null,
    )

    private companion object {
        const val MAX_URL_LENGTH = 2_048
        val HOST_PATTERN = Regex("[A-Za-z0-9._-]+")
    }
}
