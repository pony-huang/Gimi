package github.ponyhuang.gimi.domain.plugin.model

/**
 * 一个已加载动态插件的宿主侧只读描述，供设置页展示与启停。
 *
 * @property id 插件稳定唯一 id（如 `"zhihu"`）。
 * @property name 展示给用户的名称（如 `"知乎"`）。
 * @property packageName 来源插件 APK 的包名。
 * @property version 插件自身版本。
 * @property toolCount 插件注入 Agent 的工具数量。
 * @property isEnabled 是否启用（关闭后其工具不再注入 Agent）。
 */
data class PluginDescriptor(
    val id: String,
    val name: String,
    val packageName: String,
    val version: Int,
    val toolCount: Int,
    val isEnabled: Boolean,
)

/**
 * 单个配置字段的类型化描述，宿主据此渲染配置页控件。
 *
 * 这是 [github.ponyhuang.gimi.pluginapi.PluginConfigField] 在 domain 层的纯 Kotlin 投影，
 * 避免 domain 依赖 ADK/plugin-api。
 *
 * @property key 配置键，保存时回传给插件。
 * @property label 展示给用户的标签。
 * @property kind 控件类型。
 * @property secret 文本字段是否按密码/令牌隐藏输入。
 * @property defaultValue 字段默认值（TOGGLE 为 `"true"`/`"false"` 字符串）。
 * @property options SELECT 的候选项。
 */
data class PluginConfigFieldDescriptor(
    val key: String,
    val label: String,
    val kind: Kind,
    val secret: Boolean = false,
    val defaultValue: String = "",
    val options: List<String> = emptyList(),
) {
    enum class Kind { TEXT, TOGGLE, SELECT }
}

/**
 * 一个插件的配置描述（字段 + 可执行动作）。
 *
 * @property fields 需要用户填写的字段。
 * @property actions 配置页上可点击执行的动作（如「授权登录」）。
 */
data class PluginConfigDescriptor(
    val fields: List<PluginConfigFieldDescriptor> = emptyList(),
    val actions: List<PluginActionDescriptor> = emptyList(),
)

/**
 * 配置页上的一个可执行动作。
 *
 * @property id 动作唯一标识，宿主执行时传给插件。
 * @property label 按钮文案。
 */
data class PluginActionDescriptor(
    val id: String,
    val label: String,
)

/** 配置页动作的执行结果。 */
data class PluginActionOutcome(
    val message: String,
    val success: Boolean = true,
)
