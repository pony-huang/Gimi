package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.pluginapi.AgentPlugin

/**
 * 一个成功加载的动态插件。
 *
 * @property packageName 来源插件 APK 的包名。
 * @property plugin 经 DexClassLoader 实例化的插件。
 */
data class LoadedPlugin(
    val packageName: String,
    val plugin: AgentPlugin,
)

/**
 * 发现并加载已安装插件 APK 里的 [AgentPlugin] 实现。
 *
 * 宿主侧契约；具体加载逻辑见 [InstalledApkPluginLoader]。
 */
interface PluginLoader {
    fun load(): List<LoadedPlugin>
}
