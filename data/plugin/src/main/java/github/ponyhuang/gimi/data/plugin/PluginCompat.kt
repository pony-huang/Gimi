package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.pluginapi.PluginApi

/**
 * 插件协议版本兼容判定（纯逻辑，便于 JVM 单测）。
 */
internal object PluginCompat {

    /** 插件编译时固化的 [apiVersion] 是否与宿主 [PluginApi.VERSION] 兼容。 */
    fun isCompatible(apiVersion: Int): Boolean = apiVersion == PluginApi.VERSION
}
