package github.ponyhuang.gimi.feature.plugin

import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor

/** 插件列表页状态。 */
data class PluginSettingsUiState(
    val plugins: List<PluginDescriptor> = emptyList(),
    /** 是否正在重新发现插件，驱动下拉刷新指示器。 */
    val isRefreshing: Boolean = false,
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
    val callback: PluginActionCallbackUiState? = null,
) {
    val hasFields: Boolean get() = fields.isNotEmpty()
    val hasActions: Boolean get() = actions.isNotEmpty()
    val isAnyActionRunning: Boolean get() = actions.any { it.running }
}

/**
 * 配置动作等待宿主回调时的交互页状态。
 *
 * @property actionId 触发弹窗的配置页动作。
 * @property handlerId 宿主交互处理器标识。
 * @property parameters 由处理器解释的不透明参数。
 */
data class PluginActionCallbackUiState(
    val actionId: String,
    val handlerId: String,
    val parameters: Map<String, String> = emptyMap(),
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

    /**
     * 把宿主交互生成的通用键值回传插件。
     *
     * @property actionId 等待回调的配置动作。
     * @property values 插件自行解释的回调参数。
     */
    data class ReceiveActionCallback(
        val actionId: String,
        val values: Map<String, String>,
    ) : PluginConfigAction

    /** 用户取消并关闭配置动作的回调交互页。 */
    data object DismissActionCallback : PluginConfigAction
}

sealed interface PluginConfigEffect {
    /**
     * 通过宿主 Toast 展示插件动作或保存结果。
     *
     * @property message 插件返回的文本；与 [messageRes] 二选一。
     * @property messageRes 本地化文案资源 ID；与 [message] 二选一。
     */
    data class ShowToast(
        val message: String? = null,
        val messageRes: Int? = null,
    ) : PluginConfigEffect
}
