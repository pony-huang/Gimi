package github.ponyhuang.gimi.domain.conversation.runtime

import javax.inject.Qualifier

/**
 * 会话存储分区限定符：ADK 会话按 (appName, userId, sessionId) 分区，
 * 助手与 Chatbot 各自绑定一个分区实例（见 [AgentSessionIdentity]）。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AssistantConversationPartition

/** Chatbot 分区（[AgentSessionIdentity.CHATBOT_USER_ID]）的仓储/解析器限定符。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatbotConversationPartition
