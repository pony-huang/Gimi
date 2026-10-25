package github.ponyhuang.gimi.domain.a2a.repository

import github.ponyhuang.gimi.domain.a2a.model.SkippedRemoteAgent
import kotlinx.coroutines.flow.StateFlow

/**
 * 远程 A2A agent 跳过事件的报告端口：data 层在 Agent 构建期探测/装配失败时发布，
 * UI 层观察并向用户提示（与 MCP 跳过上报同构）。domain 不依赖 data 实现。
 */
interface RemoteAgentSkipReporter {

    /** 最近一次 Agent 构建中被跳过的远程 agent 集合（每次发布整体替换）。 */
    val skipped: StateFlow<List<SkippedRemoteAgent>>

    fun publish(skipped: List<SkippedRemoteAgent>)
}
