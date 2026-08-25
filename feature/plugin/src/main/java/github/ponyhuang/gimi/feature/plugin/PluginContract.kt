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
}

/** 插件配置页状态。 */
data class PluginConfigUiState(
    val pluginId: String = "",
    val fields: List<PluginConfigFieldUiState> = emptyList(),
) {
    val hasFields: Boolean get() = fields.isNotEmpty()
}

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
}
