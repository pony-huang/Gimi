package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.data.conversation.local.ChatTurnDao
import github.ponyhuang.gimi.data.conversation.local.ChatTurnEntity
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.repository.ChatTurnRepository
import github.ponyhuang.gimi.domain.conversation.repository.StaleChatTurnException
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * [ChatTurnRepository] 的持久化实现。
 *
 * 关键不变量：`ChatTurnDao` 以一个会话一行承载“当前最新发送尝试”，因此：
 * - 新发送用 [retryTurnId] = null 进入并替换该行；
 * - 重试/编辑用 [retryTurnId] 进入，记录失败尝试的 invocation ID 供 ADK Runner 官方回滚；
 * - 只有当该行仍指向同一 [ChatTurn.id] 时才具备回退资格，
 *   否则一个新的发送已覆盖它，旧失败轮失效（[StaleChatTurnException]）；
 * - [save] 仅在当前行的 [ChatTurnEntity.attemptId] 与写回一致时才落盘，旧尝试的迟到写被忽略。
 */
class AdkChatTurnRepository @Inject constructor(
    private val dao: ChatTurnDao,
) : ChatTurnRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun recover(sessionId: String): ChatTurn? {
        val entity = dao.get(sessionId) ?: return null
        val turn = json.decodeFromString<ChatTurn>(entity.turnJson)
        if (turn.status == ChatTurnStatus.RUNNING) {
            val interrupted = turn.copy(
                status = ChatTurnStatus.INTERRUPTED,
                rewindBeforeInvocationId = turn.attemptId,
            )
            save(interrupted)
            return interrupted
        }
        return turn
    }

    override suspend fun begin(
        sessionId: String,
        userMessage: Message,
        history: List<Message>,
        retryTurnId: String?,
    ): ChatTurn {
        if (retryTurnId != null) {
            val existing = dao.get(sessionId)
                ?: error("No failed turn record for session $sessionId")
            if (existing.turnId != retryTurnId) throw StaleChatTurnException()
            val failedTurn = json.decodeFromString<ChatTurn>(existing.turnJson)
            val attemptId = UUID.randomUUID().toString()
            val turn = ChatTurn(
                id = retryTurnId,
                attemptId = attemptId,
                sessionId = sessionId,
                userMessage = userMessage,
                messages = history + userMessage,
                rewindBeforeInvocationId = failedTurn.rewindBeforeInvocationId ?: failedTurn.attemptId,
            )
            dao.put(existing.copyForAttempt(attemptId, json.encodeToString(turn)))
            return turn
        }

        val turn = ChatTurn(
            id = UUID.randomUUID().toString(),
            attemptId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            userMessage = userMessage,
            messages = history + userMessage,
        )
        dao.put(
            ChatTurnEntity(
                sessionId = sessionId,
                turnId = turn.id,
                attemptId = turn.attemptId,
                turnJson = json.encodeToString(turn),
            ),
        )
        return turn
    }

    override suspend fun save(turn: ChatTurn) {
        val entity = dao.get(turn.sessionId) ?: return
        if (entity.attemptId != turn.attemptId) return
        dao.put(entity.copyForAttempt(turn.attemptId, json.encodeToString(turn)))
    }

    override suspend fun finish(sessionId: String, attemptId: String) {
        dao.finish(sessionId, attemptId)
    }

    override suspend fun delete(sessionId: String) {
        dao.delete(sessionId)
    }
}
