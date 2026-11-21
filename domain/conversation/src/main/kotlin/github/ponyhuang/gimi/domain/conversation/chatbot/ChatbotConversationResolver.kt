package github.ponyhuang.gimi.domain.conversation.chatbot

/**
 * Chatbot 分区「当前 Chatbot 会话」的解析契约：恢复当前会话或按需新建。
 *
 * Chatbot 会话由三入口共享（文本 chatbot、级联语音、原生实时），本契约只负责把调用方
 * 引到同一个当前会话上；模型归属由 Chatbot 规则在文本首条提交时锁定，语音先建的会话
 * 不预填模型。
 */
interface ChatbotConversationResolver {

    /**
     * 解析当前 Chatbot 会话：优先恢复记录的当前会话，失效则回退到最近一个会话，
     * 分区为空时新建；返回 sessionId。
     */
    suspend fun resolveCurrentOrCreate(): String

    /**
     * 激活指定的 Chatbot 会话作为当前会话；会话不存在（已被删除等）时返回 null。
     */
    suspend fun activate(sessionId: String): String?
}
