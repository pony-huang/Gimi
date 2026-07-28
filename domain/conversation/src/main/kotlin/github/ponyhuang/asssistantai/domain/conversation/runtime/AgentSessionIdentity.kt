package github.ponyhuang.asssistantai.domain.conversation.runtime

/**
 * Agent 会话的固定身份：所有 data 模块共享的 app name 与默认用户 id。
 *
 * 历史会话以这两个值持久化，任何改动都会让既有会话不可见，禁止随意修改。
 */
object AgentSessionIdentity {
    const val APP_NAME: String = "Gimi"
    const val DEFAULT_USER_ID: String = "user-default"
}
