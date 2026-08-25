package github.ponyhuang.gimi.pluginapi

/**
 * 插件配置描述 — 声明插件需要用户填写的字段与可执行的配置页动作，宿主据此动态渲染配置页。
 *
 * @property fields 需要用户填写的字段。
 * @property actions 配置页上可点击执行的动作（如「授权登录」），经
 *   [AgentPlugin.runConfigAction] 回调插件。
 */
data class PluginConfig(
    val fields: List<PluginConfigField> = emptyList(),
    val actions: List<PluginConfigAction> = emptyList(),
)

/**
 * 配置页上的一个可执行动作（按钮）。
 *
 * @property id 动作唯一标识，宿主点按时传给 [AgentPlugin.runConfigAction]。
 * @property label 按钮文案。
 */
data class PluginConfigAction(
    val id: String,
    val label: String,
)

/**
 * 单个配置字段的类型化描述。
 *
 * 宿主按 [Text]/[Toggle]/[Select] 分别用对应 Material3 控件渲染；
 * 字段值由宿主回传给插件（后续引入的回调）。
 */
sealed interface PluginConfigField {

    /** 配置键，插件据此读取宿主回传的值。 */
    val key: String

    /** 展示给用户的标签。 */
    val label: String

    /** 单行文本；[secret] 为 true 时按密码/令牌处理。 */
    data class Text(
        override val key: String,
        override val label: String,
        val defaultValue: String = "",
        val secret: Boolean = false,
    ) : PluginConfigField

    /** 布尔开关。 */
    data class Toggle(
        override val key: String,
        override val label: String,
        val defaultValue: Boolean = false,
    ) : PluginConfigField

    /** 单选下拉。 */
    data class Select(
        override val key: String,
        override val label: String,
        val options: List<String>,
        val defaultValue: String? = null,
    ) : PluginConfigField
}
