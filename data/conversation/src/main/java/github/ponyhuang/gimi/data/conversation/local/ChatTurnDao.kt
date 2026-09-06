package github.ponyhuang.gimi.data.conversation.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 最近发送轮的持久化记录。
 *
 * 一个会话只保留最新一轮（`sessionId` 为主键）；任何一次新发送都会替换该行，因此
 * [sessionId] 的当前行就代表“该会话最新发送尝试”。重试时若该行不再是目标轮，则说明
 * 用户在失败后又发起了新消息，旧失败轮不再具有回退资格（[StaleChatTurnException]）。
 *
 * @property sessionId 会话 ID（主键，一行即该会话的最新轮）。
 * @property turnId 稳定轮标识；重试/编辑沿用同一个 id，新发送生成新 id。
 * @property attemptId 本次尝试标识；串行化发送，用于拒绝旧协程的迟到写回。
 * @property turnJson [ChatTurn] 的 JSON。
 * @property checkpointJson 本轮的预发送会话检查点 JSON；重试时恢复到该状态。
 */
@Entity(tableName = "chat_turns")
data class ChatTurnEntity(
    @PrimaryKey val sessionId: String,
    val turnId: String,
    val attemptId: String,
    val turnJson: String,
    val checkpointJson: String,
) {
    fun copyForAttempt(attemptId: String, turnJson: String): ChatTurnEntity =
        copy(attemptId = attemptId, turnJson = turnJson)
}

@Dao
interface ChatTurnDao {
    @Query("SELECT * FROM chat_turns WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): ChatTurnEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ChatTurnEntity)

    @Query("DELETE FROM chat_turns WHERE sessionId = :sessionId AND attemptId = :attemptId")
    suspend fun finish(sessionId: String, attemptId: String)

    @Query("DELETE FROM chat_turns WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)
}
