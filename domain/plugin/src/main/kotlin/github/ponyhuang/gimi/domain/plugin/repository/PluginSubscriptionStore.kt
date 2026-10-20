package github.ponyhuang.gimi.domain.plugin.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * 插件订阅源地址的持久化契约。
 *
 * 支持追加多个 HTTPS 订阅源；[urls] 随新增/移除/恢复默认实时更新，供列表页自动刷新。
 */
interface PluginSubscriptionStore {
    /** 当前订阅地址列表，按添加顺序排列；可能为空。 */
    val urls: StateFlow<List<String>>

    /** 内置默认订阅地址。 */
    val defaultUrl: String

    /** 追加一个订阅地址；非法、重复或空白值应被实现方拒绝。 */
    fun add(url: String)

    /** 移除指定订阅地址。 */
    fun remove(url: String)

    /** 恢复为仅包含默认订阅地址的列表。 */
    fun reset()
}
