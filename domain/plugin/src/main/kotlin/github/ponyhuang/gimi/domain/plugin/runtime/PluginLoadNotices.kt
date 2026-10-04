package github.ponyhuang.gimi.domain.plugin.runtime

import kotlinx.coroutines.flow.Flow

/** 不兼容插件的单次通知，保留启动时产生的提示供前台 UI 消费。 */
interface PluginLoadNotices {
    /** 被跳过的插件名称；同一安装版本在进程内只提示一次。 */
    val incompatiblePluginNames: Flow<String>
}
