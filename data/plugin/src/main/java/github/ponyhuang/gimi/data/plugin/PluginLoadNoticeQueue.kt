package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.domain.plugin.runtime.PluginLoadNotices
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** 缓存加载阶段的提示；UI 尚未启动或在后台时不丢失，也不直接执行 Toast。 */
@Singleton
class PluginLoadNoticeQueue @Inject constructor() : PluginLoadNotices {
    private val pending = Channel<String>(Channel.UNLIMITED)
    private val reportedVersions = mutableMapOf<String, Long>()
    override val incompatiblePluginNames = pending.receiveAsFlow()

    @Synchronized
    fun report(packageName: String, lastUpdateTime: Long, displayName: String) {
        if (reportedVersions[packageName] == lastUpdateTime) return
        reportedVersions[packageName] = lastUpdateTime
        pending.trySend(displayName)
    }
}
