package github.ponyhuang.asssistantai.data.modelcatalog

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "model_config")
data class LLMModelConfigEntity(
    @PrimaryKey
    val serviceId: String,
    val serviceName: String,
    val displayOrder: Int,
    val modelGroupsJson: String,
    val homepageUrl: String,
    val keyHelpUrl: String,
)

@Dao
interface LLMModelConfigDao {
    @Query("SELECT * FROM model_config ORDER BY displayOrder, serviceId")
    fun observeAll(): Flow<List<LLMModelConfigEntity>>

    @Query("SELECT * FROM model_config ORDER BY displayOrder, serviceId")
    suspend fun getAll(): List<LLMModelConfigEntity>

    @Query("SELECT * FROM model_config WHERE serviceId = :serviceId")
    suspend fun get(serviceId: String): LLMModelConfigEntity?

    @Query("SELECT COUNT(*) FROM model_config")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LLMModelConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LLMModelConfigEntity>)
}

@Database(
    entities = [LLMModelConfigEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LLMModelRoomDatabase : RoomDatabase() {
    abstract fun lLMModelConfigDao(): LLMModelConfigDao

    companion object {
        const val DATABASE_NAME = "model-services.db"
    }
}

internal fun lLMModelConfigEntities(gson: Gson = Gson()): List<LLMModelConfigEntity> =
    LLMModelConfigs.services.mapIndexed { index, provider ->
        LLMModelConfigEntity(
            serviceId = provider.serviceId,
            serviceName = provider.serviceName,
            displayOrder = index,
            modelGroupsJson = gson.toJson(
                provider.lLMModelGroups.map { group ->
                    StoredModelGroup(
                        groupId = group.groupId,
                        groupName = group.groupName,
                        isExpanded = group.isExpanded,
                        models = group.models.map {
                            StoredModel(
                                modelId = it.modelId,
                                modelName = it.modelName,
                                source = StoredModelSource.REMOTE,
                                isStt = it.isStt,
                                isTts = it.isTts,
                                capabilities = it.capabilities,
                            )
                        },
                    )
                },
            ),
            homepageUrl = provider.homepageUrl,
            keyHelpUrl = provider.keyHelpUrl,
        )
    }

/**
 * 把 [LLMModelConfigs] 里缺的 provider 增量写进 Room。判断标准是 [LLMModelConfigEntity.serviceId]：
 * serviceId 已存在就跳过该条，未存在才 upsert。这样新加的 [LLMModelProvider] 在已有用户数据上也能
 * 顺利进入设置页，同时不会覆盖用户已经修改过的服务名 / 模型组等。
 *
 * 调用时机：每次启动 [ModelServiceRepository] 时执行一次；幂等，安全。
 */
internal suspend fun seedMissingModelCatalog(
    database: LLMModelRoomDatabase,
    gson: Gson = Gson(),
) {
    database.withTransaction {
        val dao = database.lLMModelConfigDao()
        val existingIds = dao.getAll().mapTo(HashSet()) { it.serviceId }
        val missing = lLMModelConfigEntities(gson)
            .filterNot { it.serviceId in existingIds }
        if (missing.isNotEmpty()) dao.upsertAll(missing)
    }
}

/**
 * Applies new built-in model metadata once for existing providers without replacing user models.
 * The caller persists a catalog version only after this transaction succeeds.
 */
internal suspend fun upgradeDefaultModelMetadata(
    database: LLMModelRoomDatabase,
    gson: Gson = Gson(),
) {
    database.withTransaction {
        val dao = database.lLMModelConfigDao()
        val defaultsById = lLMModelConfigEntities(gson).associateBy { it.serviceId }
        dao.getAll().forEach { entity ->
            val defaultEntity = defaultsById[entity.serviceId] ?: return@forEach
            val existingGroups = gson.fromJson<Array<StoredModelGroup>>(
                entity.modelGroupsJson,
                Array<StoredModelGroup>::class.java,
            ).orEmpty().toList()
            val defaultGroups = gson.fromJson<Array<StoredModelGroup>>(
                defaultEntity.modelGroupsJson,
                Array<StoredModelGroup>::class.java,
            ).orEmpty().toList()
            val upgraded = mergeDefaultModelMetadata(existingGroups, defaultGroups)
            if (upgraded != existingGroups) {
                dao.upsert(entity.copy(modelGroupsJson = gson.toJson(upgraded)))
            }
        }
    }
}

/** Persistence-only model origin used to reconcile remote and manually entered models. */
internal enum class StoredModelSource {
    REMOTE,
    USER,
}

internal data class StoredModelGroup(
    val groupId: String,
    val groupName: String,
    val isExpanded: Boolean = true,
    val models: List<StoredModel> = emptyList(),
)

internal data class StoredModel(
    val modelId: String,
    val modelName: String,
    val source: StoredModelSource,
    val isStt: Boolean = false,
    val isTts: Boolean = false,
    val capabilities: github.ponyhuang.asssistantai.domain.modelcatalog.model.MultimodalCapabilities = github.ponyhuang.asssistantai.domain.modelcatalog.model.MultimodalCapabilities(),
)
