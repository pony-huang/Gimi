package github.ponyhuang.asssistantai.data

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

/** Room-owned public catalog for one model provider. Sensitive connection settings live elsewhere. */
@Entity(tableName = "model_services")
data class ModelServiceEntity(
    @PrimaryKey
    val serviceId: String,
    val serviceName: String,
    val displayOrder: Int,
    val modelGroupsJson: String,
    val homepageUrl: String,
    val keyHelpUrl: String,
    val docsUrl: String,
    val modelsUrl: String,
)

@Dao
interface ModelServiceDao {
    @Query("SELECT * FROM model_services ORDER BY displayOrder, serviceId")
    fun observeAll(): Flow<List<ModelServiceEntity>>

    @Query("SELECT * FROM model_services ORDER BY displayOrder, serviceId")
    suspend fun getAll(): List<ModelServiceEntity>

    @Query("SELECT * FROM model_services WHERE serviceId = :serviceId")
    suspend fun get(serviceId: String): ModelServiceEntity?

    @Query("SELECT COUNT(*) FROM model_services")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModelServiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ModelServiceEntity>)
}

@Database(
    entities = [ModelServiceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ModelServiceDatabase : RoomDatabase() {
    abstract fun modelServiceDao(): ModelServiceDao
}

internal fun defaultModelServiceEntities(gson: Gson = Gson()): List<ModelServiceEntity> =
    DefaultModelServices.services.mapIndexed { index, provider ->
        ModelServiceEntity(
            serviceId = provider.serviceId,
            serviceName = provider.serviceName,
            displayOrder = index,
            modelGroupsJson = gson.toJson(
                provider.LLMModelGroups.map { group ->
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
                            )
                        },
                    )
                },
            ),
            homepageUrl = provider.homepageUrl,
            keyHelpUrl = provider.keyHelpUrl,
            docsUrl = provider.docsUrl,
            modelsUrl = provider.modelsUrl,
        )
    }

/**
 * 把 [DefaultModelServices] 里缺的 provider 增量写进 Room。判断标准是 [ModelServiceEntity.serviceId]：
 * serviceId 已存在就跳过该条，未存在才 upsert。这样新加的 [LLMModelProvider] 在已有用户数据上也能
 * 顺利进入设置页，同时不会覆盖用户已经修改过的服务名 / 模型组等。
 *
 * 调用时机：每次启动 [ModelServiceRepository] 时执行一次；幂等，安全。
 */
internal suspend fun seedMissingModelCatalog(
    database: ModelServiceDatabase,
    gson: Gson = Gson(),
) {
    database.withTransaction {
        val dao = database.modelServiceDao()
        val existingIds = dao.getAll().mapTo(HashSet()) { it.serviceId }
        val missing = defaultModelServiceEntities(gson)
            .filterNot { it.serviceId in existingIds }
        if (missing.isNotEmpty()) dao.upsertAll(missing)
    }
}

/**
 * Applies new built-in model metadata once for existing providers without replacing user models.
 * The caller persists a catalog version only after this transaction succeeds.
 */
internal suspend fun upgradeDefaultModelMetadata(
    database: ModelServiceDatabase,
    gson: Gson = Gson(),
) {
    database.withTransaction {
        val dao = database.modelServiceDao()
        val defaultsById = defaultModelServiceEntities(gson).associateBy { it.serviceId }
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
)
