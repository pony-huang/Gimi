package github.ponyhuang.gimi.pluginapi

/**
 * 插件协议常量 — 宿主与插件共享的稳定契约。
 *
 * 插件 APK 在 manifest 里声明发现组件（intent-filter action）与实现类（meta-data），
 * 宿主据此发现并加载。破坏性变更需递增 [VERSION]，宿主据此拒绝不兼容插件。
 */
object PluginApi {

    /** 插件协议兼容性主版本；宿主与插件必须一致。 */
    const val VERSION: Int = BuildConfig.PLUGIN_API_VERSION

    /** 宿主实际编译使用的 ADK 版本，由版本目录生成，升级依赖时自动更新。 */
    const val ADK_VERSION: String = BuildConfig.ADK_VERSION

    /** APK 在加载实现类之前声明的插件 API 版本。 */
    const val API_VERSION_META_DATA_KEY: String = "github.ponyhuang.gimi.plugin.API_VERSION"

    /** APK 编译使用的 ADK 版本；运行时共用宿主 ADK，必须一致。 */
    const val ADK_VERSION_META_DATA_KEY: String = "github.ponyhuang.gimi.plugin.ADK_VERSION"

    /** 插件声明发现组件的 intent-filter action。 */
    const val DISCOVERY_ACTION: String = "github.ponyhuang.gimi.plugin.DISCOVERY"

    /** 插件在 `<application>` 下用 `<meta-data>` 声明实现类全名的 key。 */
    const val CLASS_META_DATA_KEY: String = "github.ponyhuang.gimi.plugin.CLASS"
}
