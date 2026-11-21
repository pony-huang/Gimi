package github.ponyhuang.gimi.domain.conversation.runtime

/**
 * Agent 会话的固定身份：所有 data 模块共享的 app name 与默认用户 id。
 *
 * 历史会话以这两个值持久化，任何改动都会让既有会话不可见，禁止随意修改。
 */
object AgentSessionIdentity {
    const val APP_NAME: String = "Gimi"
    const val DEFAULT_USER_ID: String = "user-default"
    /**
     * Chatbot 形态专用的用户 id。
     *
     * 会话存储按 (app name, user id, session id) 分区，抽屉列表也按用户 id 过滤；
     * Chatbot 用独立用户 id，助手聊天就看不到这些会话。
     */
    const val CHATBOT_USER_ID: String = "user-chatbot"
    /** 文字聊天与语音事件共用的助手作者；大小写不同会被 SDK 当作另一位 agent。 */
    const val ASSISTANT_NAME: String = "Assistant"
    /** Chatbot 形态的 agent 名；与助手作者分开，事件作者不会互相覆盖。 */
    const val CHATBOT_NAME: String = "Chatbot"

    /** SDK 以作者名区分消息来源；用户侧字面量同样必须全局唯一，避免与助手名漂移。 */
    const val USER_AUTHOR: String = "user"
}
