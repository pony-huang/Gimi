package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin

/**
 * 一次 Agent 构建的完整输入。
 *
 * 把模型选择、工具访问模式、推理强度和插件运行时快照收敛为单一不可变参数对象：
 * 调用方只组装一次，[AgentFactory.create] 与 [AgentChatRunner] 的 factory lambda
 * 签名不再随后续新增构建旋钮变化。
 *
 * @property selection 显式模型选择；为 null 时按"聊天页当前选择 → 默认模型"解析。
 * @property toolAccessMode 工具声明加载模式，决定直接声明与 `tool_search` 检索的分界。
 * @property reasoningEffort 当前会话的推理强度，映射为 ADK ThinkingLevel。
 * @property pluginRuntime 与本次构建绑定的插件运行时快照；由缓存查找时点统一捕获，
 *   保证插件 revision（缓存键组成部分）与实际装配的插件一致。
 */
data class AgentBuildSpec(
    val selection: ModelSelection? = null,
    val toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val pluginRuntime: PluginRuntimeSnapshot<AgentPlugin>,
)
