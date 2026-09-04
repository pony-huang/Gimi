package github.ponyhuang.gimi.domain.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/**
 * 可执行的当前聊天会话快照。
 *
 * @property sessionId 当前会话 ID。
 * @property modelSelection 本轮应沿用的会话模型。
 * @property toolConfiguration 本轮应沿用的会话工具配置。
 */
data class ConversationSessionSnapshot(
    val sessionId: String,
    val modelSelection: ModelSelection,
    val toolConfiguration: ConversationToolConfiguration,
)

/** 统一恢复、创建和初始化聊天当前会话。 */
interface ConversationSessionResolver {
    /** 恢复当前会话；没有可用会话时按普通聊天默认配置创建一个。 */
    suspend fun resolveCurrentOrCreate(): ConversationSessionSnapshot

    /** 创建并激活一个使用普通聊天默认配置的新会话。 */
    suspend fun createAndActivate(): ConversationSessionSnapshot

    /** 激活指定会话；会话不存在时返回 null。 */
    suspend fun activate(sessionId: String): ConversationSessionSnapshot?

    /** 重新解析并持久化指定会话的有效工具配置，不改变当前激活会话。 */
    suspend fun resolveToolConfiguration(
        sessionId: String,
        modelSelection: ModelSelection,
    ): ConversationToolConfiguration
}
