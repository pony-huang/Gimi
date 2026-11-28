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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Metadata owned by the app for an ADK session. Session events remain in ADK's database.
 *
 * `lastMessage` 是抽屉行的预览文本：ADK 的 `listSessions` 不返回事件，若每行都去读会话
 * 全量历史只为算预览，代价随会话长度线性增长。预览是应用自己的展示数据，因此在这里持久化。
 *
 * `partition` 是会话存储分区（ADK 的 user id）：助手与 Chatbot 共用本库但各占一个分区，
 * 「当前会话」标记（isLast）按分区各自生效，一个分区激活不得清除另一个分区的指针。
 */
@Entity(
    tableName = "conversation_metadata",
    primaryKeys = ["partition", "sessionId"],
    indices = [
        Index(
            value = ["partition", "isLast"],
            name = "index_conversation_metadata_partition_isLast",
        ),
    ],
)
data class ConversationMetadataEntity(
    val partition: String,
    val sessionId: String,
    val model: String = "",
    val isLast: Boolean = false,
    val toolConfigurationJson: String = "",
    val lastMessage: String = "",
)

@Dao
interface ConversationMetadataDao {
    @Query("SELECT * FROM conversation_metadata WHERE partition = :partition")
    suspend fun getAll(partition: String): List<ConversationMetadataEntity>

    @Query("SELECT * FROM conversation_metadata WHERE partition = :partition AND sessionId = :sessionId")
    suspend fun get(partition: String, sessionId: String): ConversationMetadataEntity?

    @Query("SELECT * FROM conversation_metadata WHERE partition = :partition AND isLast = 1 LIMIT 1")
    suspend fun getLast(partition: String): ConversationMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationMetadataEntity)

    @Query("UPDATE conversation_metadata SET isLast = 0 WHERE partition = :partition AND isLast = 1")
    suspend fun clearLast(partition: String)

    @Query("DELETE FROM conversation_metadata WHERE partition = :partition AND sessionId = :sessionId")
    suspend fun delete(partition: String, sessionId: String)

    @Query(
        "UPDATE conversation_metadata " +
            "SET toolConfigurationJson = :configurationJson " +
            "WHERE partition = :partition AND sessionId = :sessionId",
    )
    suspend fun setToolConfiguration(partition: String, sessionId: String, configurationJson: String)

    /** 更新抽屉行预览；只改一列，不重写整行。 */
    @Query("UPDATE conversation_metadata SET lastMessage = :lastMessage WHERE partition = :partition AND sessionId = :sessionId")
    suspend fun setLastMessage(partition: String, sessionId: String, lastMessage: String)

    /** Atomically makes [sessionId] the sole current conversation within [partition]. */
    @Transaction
    suspend fun activate(partition: String, sessionId: String, defaultModel: String): ConversationMetadataEntity {
        clearLast(partition)
        val active = get(partition, sessionId)?.copy(isLast = true)
            ?: ConversationMetadataEntity(
                partition = partition,
                sessionId = sessionId,
                model = defaultModel,
                isLast = true,
            )
        upsert(active)
        return active
    }

    /** Updates a session's model while preserving its current-session state. */
    @Transaction
    suspend fun setModel(partition: String, sessionId: String, model: String) {
        val updated = get(partition, sessionId)?.copy(model = model)
            ?: ConversationMetadataEntity(partition = partition, sessionId = sessionId, model = model)
        upsert(updated)
    }
}

/** 一条已上传到厂商文件服务的注册记录；fileId 是厂商侧真源，本地另存归档绑定供打开兜底。 */
@Entity(
    tableName = "uploaded_files",
    indices = [
        Index(value = ["vendor"], name = "index_uploaded_files_vendor"),
    ],
)
data class UploadedFileEntity(
    @PrimaryKey
    val fileId: String,
    val vendor: String,
    val displayName: String,
    val mimeType: String,
    val bytes: Long,
    val createdAtEpochSeconds: Long,
    /** 绑定的本地归档副本路径；发送归档或下载兜底成功时回写，null 表示尚无绑定。 */
    val localPath: String? = null,
)

@Dao
interface UploadedFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UploadedFileEntity)

    @Query("SELECT * FROM uploaded_files WHERE vendor = :vendor ORDER BY createdAtEpochSeconds DESC")
    fun observeForVendor(vendor: String): Flow<List<UploadedFileEntity>>

    /** 查询 fileId 绑定的本地归档副本路径；无记录或无绑定时返回 null。 */
    @Query("SELECT localPath FROM uploaded_files WHERE fileId = :fileId AND vendor = :vendor")
    suspend fun localPath(fileId: String, vendor: String): String?

    /** 回写本地归档绑定；记录不存在时无行可改，静默幂等。 */
    @Query("UPDATE uploaded_files SET localPath = :localPath WHERE fileId = :fileId AND vendor = :vendor")
    suspend fun bindLocalPath(fileId: String, vendor: String, localPath: String)

    /** 厂商侧文件被删除后移除本地记录；不存在时静默幂等。 */
    @Query("DELETE FROM uploaded_files WHERE fileId = :fileId AND vendor = :vendor")
    suspend fun delete(fileId: String, vendor: String)
}

@Database(
    entities = [ConversationMetadataEntity::class, UploadedFileEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class ConversationMetadataDatabase : RoomDatabase() {
    abstract fun conversationMetadataDao(): ConversationMetadataDao

    abstract fun uploadedFileDao(): UploadedFileDao
    companion object {
        const val DATABASE_NAME = "conversation-metadata.db"

        /**
         * 7 → 8：为 uploaded_files 增加可空的本地归档绑定列。
         *
         * 非破坏性迁移保留既有会话元数据与已上传文件记录——这些是用户数据，新增一列
         * 没有理由清空整库。列名与 [UploadedFileEntity.localPath] 一致；SQLite 的
         * ADD COLUMN ... TEXT 默认 NULL，与 Kotlin 侧默认值 null 对齐。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE uploaded_files ADD COLUMN localPath TEXT")
            }
        }
    }
}
