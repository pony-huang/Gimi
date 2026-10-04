package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.pluginapi.PluginApi

/**
 * 插件协议版本兼容判定（纯逻辑，便于 JVM 单测）。
 */
internal object PluginCompat {

    /** 缺少声明的旧插件同样拒绝；ADK 不保证跨版本二进制兼容，须完全一致。 */
    fun isCompatible(apiVersion: Int?, adkVersion: String?): Boolean =
        apiVersion == PluginApi.VERSION && adkVersion == PluginApi.ADK_VERSION

    /** 插件编译时固化的 [apiVersion] 是否与宿主 [PluginApi.VERSION] 兼容。 */
    fun isCompatible(apiVersion: Int): Boolean = apiVersion == PluginApi.VERSION
}
