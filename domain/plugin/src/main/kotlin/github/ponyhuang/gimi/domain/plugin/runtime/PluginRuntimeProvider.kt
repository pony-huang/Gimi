package github.ponyhuang.gimi.domain.plugin.runtime

import kotlinx.coroutines.flow.StateFlow

/**
 * 插件运行时在同一时点可供消费者使用的不可变状态。
 *
 * @property revision 运行时有效状态的单调递增版本，用于使消费者缓存失效。
 * @property enabledPlugins 当前启用的插件贡献；列表具有只读语义。
 */
data class PluginRuntimeSnapshot<T : Any>(
    val revision: Long,
    val enabledPlugins: List<T>,
)

/**
 * 向运行时消费者提供类型安全插件贡献的领域契约。
 *
 * 泛型类型由组合层决定，使 domain 无需依赖具体插件 SPI 或运行时 SDK。
 */
interface PluginRuntimeProvider<T : Any> {
    /** 修订号与启用贡献组成的原子状态快照。 */
    val runtime: StateFlow<PluginRuntimeSnapshot<T>>
}
