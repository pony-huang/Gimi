package github.ponyhuang.gimi.domain.conversation.runtime

/**
 * Agent 形态：决定装配哪些能力来源。
 *
 * - [ASSISTANT]：完整助手形态，挂载本地工具、MCP、插件、技能、远程 agent 等全部贡献方。
 * - [CHATBOT]：Chatbot 形态，只挂官方工具里的联网检索能力，不声明任何设备侧工具。
 *
 * 形态参与 Agent 运行时的缓存键：同一模型下两种形态是两个不同的 Agent，不互相复用。
 */
enum class AgentProfile {
    ASSISTANT,
    CHATBOT,
}
