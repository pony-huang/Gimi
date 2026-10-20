package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 可选 OAuth 动作协议：声明状态动作启用组件，登录动作沿用配置动作执行通道。 */
internal const val OAUTH_STATUS_ACTION = "oauth_status"
internal const val OAUTH_LOGIN_ACTION = "oauth_login"
internal const val CONFIG_LINK_ACTION_PREFIX = "link:"

/** OAuth 状态；检查失败不等同于未登录。 */
enum class PluginOAuthAuthorization { Checking, Authorized, Unauthorized, Failed }

/**
 * 公共 OAuth 组件状态，不包含服务商、令牌或配置字段约定。
 * @property pluginId 当前检查对应的插件。
 * @property enabled 插件是否声明可选 OAuth 状态动作。
 * @property authorization 最近一次会话检查的结果。
 */
data class PluginOAuthConfigUiState(
    val pluginId: String = "",
    val enabled: Boolean = false,
    val authorization: PluginOAuthAuthorization = PluginOAuthAuthorization.Checking,
)

/**
 * 通用 OAuth 状态控制器：通过现有配置动作协议读取，实际鉴权始终由插件实现。
 * `oauth_status` 成功结果为 `authorized` 或 `unauthorized`；失败或未知结果表示检查失败。
 */
@HiltViewModel
class PluginOAuthConfigViewModel @Inject constructor(
    private val repository: PluginRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PluginOAuthConfigUiState())
    val state = mutableState.asStateFlow()
    private var checkJob: Job? = null

    fun bind(pluginId: String, enabled: Boolean, busy: Boolean) {
        if (mutableState.value.pluginId != pluginId || mutableState.value.enabled != enabled) {
            cancelCheck()
            mutableState.value = PluginOAuthConfigUiState(pluginId = pluginId, enabled = enabled)
        }
        if (busy || !enabled) cancelCheck() else refresh()
    }

    fun cancelCheck() {
        checkJob?.cancel()
    }

    fun refresh() {
        val current = mutableState.value
        if (!current.enabled || checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            mutableState.update { it.copy(authorization = PluginOAuthAuthorization.Checking) }
            val authorization = try {
                val result = repository.runAction(current.pluginId, OAUTH_STATUS_ACTION) as? PluginActionExecution.Completed
                when {
                    result?.outcome?.success != true -> PluginOAuthAuthorization.Failed
                    result.outcome.message == "authorized" -> PluginOAuthAuthorization.Authorized
                    result.outcome.message == "unauthorized" -> PluginOAuthAuthorization.Unauthorized
                    else -> PluginOAuthAuthorization.Failed
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 外部插件边界：检查失败仅表示无法确认，不能误报为已退出。
                PluginOAuthAuthorization.Failed
            }
            mutableState.update {
                if (it.pluginId == current.pluginId && it.enabled) it.copy(authorization = authorization) else it
            }
        }
    }
}
