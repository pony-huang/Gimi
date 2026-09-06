package github.ponyhuang.gimi.domain.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.Message

/** 失败轮次日志与会话检查点；所有恢复操作都必须在持有会话运行锁时执行。 */
interface ChatTurnRepository {
    /** 加载并恢复被进程终止中断的轮次；不发送网络请求。 */
    suspend fun recover(sessionId: String): ChatTurn?

    /** 先持久化请求和检查点，再返回可开始运行的尝试。重试必须校验轮次仍为最新。 */
    suspend fun begin(
        sessionId: String,
        userMessage: Message,
        history: List<Message>,
        retryTurnId: String? = null,
    ): ChatTurn

    /** 仅当前尝试可更新日志，旧尝试的写入被忽略。 */
    suspend fun save(turn: ChatTurn)

    /** 成功时结束恢复能力；失败与用户主动停止保留为可恢复轮，新发送会使其失效。不删除会话历史。 */
    suspend fun finish(sessionId: String, attemptId: String)

    suspend fun delete(sessionId: String)
}

/** 失败轮次已被后续消息取代，不能回退当前历史。 */
class StaleChatTurnException : IllegalStateException("The failed turn is no longer the latest turn.")
