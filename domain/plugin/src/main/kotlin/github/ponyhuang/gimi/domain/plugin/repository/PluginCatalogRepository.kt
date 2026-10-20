package github.ponyhuang.gimi.domain.plugin.repository

import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogLoad

/** 插件订阅目录契约；实现方负责远端订阅、内置回退与解析。 */
interface PluginCatalogRepository {
    /**
     * 读取当前订阅目录。
     *
     * @param force 忽略进程内缓存，强制重新请求远端订阅。
     */
    suspend fun load(force: Boolean = false): PluginCatalogLoad
}
