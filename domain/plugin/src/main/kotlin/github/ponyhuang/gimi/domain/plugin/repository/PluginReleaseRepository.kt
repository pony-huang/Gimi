package github.ponyhuang.gimi.domain.plugin.repository

import github.ponyhuang.gimi.domain.plugin.model.PluginRelease

/** 插件发布信息来源契约；当前实现读取本仓库的最新 GitHub Release。 */
interface PluginReleaseRepository {
    suspend fun latestRelease(): PluginRelease
}
