package github.ponyhuang.gimi.data.conversation.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * Metadata owned by the app for an ADK session. Session events remain in ADK's database.
 *
 * `lastMessage` 是抽屉行的预览文本：ADK 的 `listSessions` 不返回事件，若每行都去读会话
 * 全量历史只为算预览，代价随会话长度线性增长。预览是应用自己的展示数据，因此在这里持久化。
 */
@Entity(
    tableName = "conversation_metadata",
    indices = [
        Index(
            value = ["isLast"],
            name = "index_conversation_metadata_isLast",
        ),
    ],
)
data class ConversationMetadataEntity(
    @PrimaryKey
    val sessionId: String,
    val model: String = "",
    val isLast: Boolean = false,
    val toolConfigurationJson: String = "",
    val lastMessage: String = "",
)

@Dao
interface ConversationMetadataDao {
    @Query("SELECT * FROM conversation_metadata")
    suspend fun getAll(): List<ConversationMetadataEntity>

    @Query("SELECT * FROM conversation_metadata WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): ConversationMetadataEntity?

    @Query("SELECT * FROM conversation_metadata WHERE isLast = 1 LIMIT 1")
    suspend fun getLast(): ConversationMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationMetadataEntity)

    @Query("UPDATE conversation_metadata SET isLast = 0 WHERE isLast = 1")
    suspend fun clearLast()

    @Query("DELETE FROM conversation_metadata WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query(
        "UPDATE conversation_metadata " +
            "SET toolConfigurationJson = :configurationJson " +
            "WHERE sessionId = :sessionId",
    )
    suspend fun setToolConfiguration(sessionId: String, configurationJson: String)

    /** 更新抽屉行预览；只改一列，不重写整行。 */
    @Query("UPDATE conversation_metadata SET lastMessage = :lastMessage WHERE sessionId = :sessionId")
    suspend fun setLastMessage(sessionId: String, lastMessage: String)

    /** Atomically makes [sessionId] the sole current conversation. */
    @Transaction
    suspend fun activate(sessionId: String, defaultModel: String): ConversationMetadataEntity {
        clearLast()
        val active = get(sessionId)?.copy(isLast = true)
            ?: ConversationMetadataEntity(
                sessionId = sessionId,
                model = defaultModel,
                isLast = true,
            )
        upsert(active)
        return active
    }

    /** Updates a session's model while preserving its current-session state. */
    @Transaction
    suspend fun setModel(sessionId: String, model: String) {
        val updated = get(sessionId)?.copy(model = model)
            ?: ConversationMetadataEntity(sessionId = sessionId, model = model)
        upsert(updated)
    }
}

@Database(
    entities = [ConversationMetadataEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class ConversationMetadataDatabase : RoomDatabase() {
    abstract fun conversationMetadataDao(): ConversationMetadataDao
    companion object {
        const val DATABASE_NAME = "conversation-metadata.db"
    }
}
