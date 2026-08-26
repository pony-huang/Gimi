package github.ponyhuang.gimi.pluginapi

/**
 * 插件协议常量 — 宿主与插件共享的稳定契约。
 *
 * 插件 APK 在 manifest 里声明发现组件（intent-filter action）与实现类（meta-data），
 * 宿主据此发现并加载。破坏性变更需递增 [VERSION]，宿主据此拒绝不兼容插件。
 */
object PluginApi {

    /** 插件协议兼容性主版本；宿主与插件必须一致。 */
    const val VERSION: Int = 2

    /** 插件声明发现组件的 intent-filter action。 */
    const val DISCOVERY_ACTION: String = "github.ponyhuang.gimi.plugin.DISCOVERY"

    /** 插件在 `<application>` 下用 `<meta-data>` 声明实现类全名的 key。 */
    const val CLASS_META_DATA_KEY: String = "github.ponyhuang.gimi.plugin.CLASS"
}
