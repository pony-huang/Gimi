package github.ponyhuang.asssistantai.data.conversation.local

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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Metadata owned by the app for an ADK session. Session events remain in ADK's database. */
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
    val toolConfigurationJson: String? = null,
)

@Dao
abstract class ConversationMetadataDao {
    @Query("SELECT * FROM conversation_metadata")
    abstract suspend fun getAll(): List<ConversationMetadataEntity>

    @Query("SELECT * FROM conversation_metadata WHERE sessionId = :sessionId")
    abstract suspend fun get(sessionId: String): ConversationMetadataEntity?

    @Query("SELECT * FROM conversation_metadata WHERE isLast = 1 LIMIT 1")
    abstract suspend fun getLast(): ConversationMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: ConversationMetadataEntity)

    @Query("UPDATE conversation_metadata SET isLast = 0 WHERE isLast = 1")
    abstract suspend fun clearLast()

    @Query("DELETE FROM conversation_metadata WHERE sessionId = :sessionId")
    abstract suspend fun delete(sessionId: String)

    @Query(
        "UPDATE conversation_metadata " +
            "SET toolConfigurationJson = :configurationJson " +
            "WHERE sessionId = :sessionId",
    )
    abstract suspend fun setToolConfiguration(sessionId: String, configurationJson: String)

    /** Atomically makes [sessionId] the sole current conversation. */
    @Transaction
    open suspend fun activate(sessionId: String, defaultModel: String): ConversationMetadataEntity {
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
    open suspend fun setModel(sessionId: String, model: String) {
        val updated = get(sessionId)?.copy(model = model)
            ?: ConversationMetadataEntity(sessionId = sessionId, model = model)
        upsert(updated)
    }
}

@Database(
    entities = [ConversationMetadataEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class ConversationMetadataDatabase : RoomDatabase() {
    abstract fun conversationMetadataDao(): ConversationMetadataDao

    companion object {
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversation_metadata " +
                        "ADD COLUMN toolConfigurationJson TEXT DEFAULT NULL",
                )
            }
        }
    }
}
