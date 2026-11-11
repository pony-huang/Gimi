package github.ponyhuang.gimi.domain.conversation.runtime

/**
 * Agent 会话的固定身份：所有 data 模块共享的 app name 与默认用户 id。
 *
 * 历史会话以这两个值持久化，任何改动都会让既有会话不可见，禁止随意修改。
 */
object AgentSessionIdentity {
    const val APP_NAME: String = "Gimi"
    const val DEFAULT_USER_ID: String = "user-default"
    /** 文字聊天与语音事件共用的助手作者；大小写不同会被 SDK 当作另一位 agent。 */
    const val ASSISTANT_NAME: String = "Assistant"

    /** SDK 以作者名区分消息来源；用户侧字面量同样必须全局唯一，避免与助手名漂移。 */
    const val USER_AUTHOR: String = "user"
}
