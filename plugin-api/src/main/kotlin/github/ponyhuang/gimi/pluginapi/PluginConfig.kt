package github.ponyhuang.gimi.pluginapi

/**
 * 插件配置描述 — 声明插件需要用户填写的字段，宿主据此动态渲染配置页。
 */
data class PluginConfig(
    val fields: List<PluginConfigField> = emptyList(),
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
