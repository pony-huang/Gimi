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
 * @property thinkingEnabled 是否开启模型思考。
 * @property reasoningEffort 当前会话的推理强度，映射为 ADK ThinkingLevel。
 * @property pluginRuntime 与本次构建绑定的插件运行时快照；由缓存查找时点统一捕获，
 *   保证插件 revision（缓存键组成部分）与实际装配的插件一致。
 * @property instructionSuffix 追加在默认助手指令之后的场景化补充（如实时语音的朗读要求）；
 *   参与缓存键，不同后缀各自构建 Agent。
 * @property voiceResponse 是否采用语音输出契约；参与缓存键，避免复用文字格式指令。
 * @property a2uiSupported 调用方是否提供 A2UI 渲染能力（执行时传入 capabilities）。
 *   为 false 时不声明 `generate_a2ui`，避免语音等无渲染端看到必然失败的工具；参与缓存键。
 */
data class AgentBuildSpec(
    val selection: ModelSelection? = null,
    val toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val pluginRuntime: PluginRuntimeSnapshot<AgentPlugin>,
    val thinkingEnabled: Boolean = true,
    val instructionSuffix: String = "",
    val a2uiSupported: Boolean = true,
    val voiceResponse: Boolean = false,
)
