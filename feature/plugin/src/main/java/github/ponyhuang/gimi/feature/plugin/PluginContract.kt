package github.ponyhuang.gimi.feature.plugin

import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor

/** 插件列表页状态。 */
data class PluginSettingsUiState(
    val plugins: List<PluginDescriptor> = emptyList(),
)

sealed interface PluginSettingsAction {
    /** 切换插件启用状态。 */
    data class SetEnabled(val pluginId: String, val enabled: Boolean) : PluginSettingsAction

    /** 重新发现已安装插件（无需重启）。 */
    data object Refresh : PluginSettingsAction
}

sealed interface PluginSettingsEffect {
    /** 发现新安装的插件。 */
    data class ShowPluginAdded(val pluginIds: List<String>) : PluginSettingsEffect
}

/** 插件配置页状态。 */
data class PluginConfigUiState(
    val pluginId: String = "",
    val fields: List<PluginConfigFieldUiState> = emptyList(),
    val actions: List<PluginActionUiState> = emptyList(),
    val notice: PluginNotice? = null,
    val browser: PluginBrowserUiState? = null,
) {
    val hasFields: Boolean get() = fields.isNotEmpty()
    val hasActions: Boolean get() = actions.isNotEmpty()
    val isAnyActionRunning: Boolean get() = actions.any { it.running }
}

/**
 * 内置浏览器授权页状态。
 *
 * @property actionId 触发弹窗的配置页动作。
 * @property authorizeUrl WebView 加载的授权 URL。
 * @property redirectBase WebView 应拦截的重定向前缀，截获后交给
 *   [PluginConfigAction.CompleteAction]。
 */
data class PluginBrowserUiState(
    val actionId: String,
    val authorizeUrl: String,
    val redirectBase: String,
)

/**
 * 配置页动作的可点击状态。
 *
 * @property id 动作标识。
 * @property label 按钮文案。
 * @property running 是否正在执行（执行中按钮禁用）。
 */
data class PluginActionUiState(
    val id: String,
    val label: String,
    val running: Boolean = false,
)

/**
 * 配置页一次性提示。
 *
 * @property message 插件返回的文本（动作结果，可能非本地化）。
 * @property messageRes 本地化文案资源 ID（如「配置已保存」）；与 [message] 二选一。
 * @property isError 是否为错误提示。
 */
data class PluginNotice(
    val message: String? = null,
    val messageRes: Int? = null,
    val isError: Boolean = false,
)

/**
 * 配置页单个字段的可编辑状态。
 *
 * @property value 当前字符串值（TOGGLE 为 `"true"`/`"false"`）。
 */
data class PluginConfigFieldUiState(
    val key: String,
    val label: String,
    val kind: PluginConfigFieldDescriptor.Kind,
    val secret: Boolean = false,
    val options: List<String> = emptyList(),
    val value: String = "",
)

sealed interface PluginConfigAction {
    data class SetValue(val key: String, val value: String) : PluginConfigAction
    data object Save : PluginConfigAction
    data class RunAction(val actionId: String) : PluginConfigAction
    data object DismissNotice : PluginConfigAction

    /** 内置浏览器截获重定向后，把完整重定向 URL 交给插件完成动作。 */
    data class CompleteAction(val actionId: String, val redirectUrl: String) : PluginConfigAction

    /** 关闭内置浏览器授权页（用户取消）。 */
    data object CloseBrowser : PluginConfigAction
}
