package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.pluginapi.PluginConfigField

/**
 * 把 plugin-api 的 [PluginConfigField] 投影为 domain 层的纯 Kotlin [PluginConfigFieldDescriptor]，
 * 供设置页渲染。字段值一律以字符串承载（与 [PluginConfigStore] 的存储格式一致）。
 */
internal fun PluginConfigField.toDescriptor(): PluginConfigFieldDescriptor = when (this) {
    is PluginConfigField.Text -> PluginConfigFieldDescriptor(
        key = key,
        label = label,
        kind = PluginConfigFieldDescriptor.Kind.TEXT,
        secret = secret,
        defaultValue = defaultValue,
    )
    is PluginConfigField.Toggle -> PluginConfigFieldDescriptor(
        key = key,
        label = label,
        kind = PluginConfigFieldDescriptor.Kind.TOGGLE,
        defaultValue = defaultValue.toString(),
    )
    is PluginConfigField.Select -> PluginConfigFieldDescriptor(
        key = key,
        label = label,
        kind = PluginConfigFieldDescriptor.Kind.SELECT,
        options = options,
        defaultValue = defaultValue.orEmpty(),
    )
}
