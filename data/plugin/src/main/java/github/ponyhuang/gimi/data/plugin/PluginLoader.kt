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
    /** 返回已加载插件（首次调用触发发现）。 */
    fun load(): List<LoadedPlugin>

    /**
     * 重新发现已安装的插件 APK，**只新增**此前未加载的包并实例化；
     * 已加载插件保持原实例（避免重复实例化与类加载器冲突），已卸载的包从缓存移除。
     *
     * @return 本次新增的插件。
     */
    fun refresh(): List<LoadedPlugin>
}
