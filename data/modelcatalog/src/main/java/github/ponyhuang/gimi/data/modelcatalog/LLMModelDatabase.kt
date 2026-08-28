package github.ponyhuang.gimi.data.modelcatalog

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 兼容 Gson 旧持久化格式的 kotlinx Json：字段名 = Kotlin 属性名、枚举按 name、
 * 默认值全量写出、null 省略、忽略未知键、容忍类型不匹配。
 */
internal val modelCatalogJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    coerceInputValues = true
}

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

internal fun lLMModelConfigEntities(): List<LLMModelConfigEntity> =
    LLMModelConfigs.services.mapIndexed { index, provider ->
        LLMModelConfigEntity(
            serviceId = provider.serviceId,
            serviceName = provider.serviceName,
            displayOrder = index,
            modelGroupsJson = modelCatalogJson.encodeToString(
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
) {
    database.withTransaction {
        val dao = database.lLMModelConfigDao()
        val existingIds = dao.getAll().mapTo(HashSet()) { it.serviceId }
        val missing = lLMModelConfigEntities()
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
) {
    database.withTransaction {
        val dao = database.lLMModelConfigDao()
        val defaultsById = lLMModelConfigEntities().associateBy { it.serviceId }
        dao.getAll().forEach { entity ->
            val defaultEntity = defaultsById[entity.serviceId] ?: return@forEach
            val existingGroups = modelCatalogJson
                .decodeFromString<List<StoredModelGroup>>(entity.modelGroupsJson)
            val defaultGroups = modelCatalogJson
                .decodeFromString<List<StoredModelGroup>>(defaultEntity.modelGroupsJson)
            val upgraded = mergeDefaultModelMetadata(existingGroups, defaultGroups)
            if (upgraded != existingGroups) {
                dao.upsert(entity.copy(modelGroupsJson = modelCatalogJson.encodeToString(upgraded)))
            }
        }
    }
}

/** Persistence-only model origin used to reconcile remote and manually entered models. */
@Serializable
internal enum class StoredModelSource {
    REMOTE,
    USER,
}

@Serializable
internal data class StoredModelGroup(
    val groupId: String,
    val groupName: String,
    val isExpanded: Boolean = true,
    val models: List<StoredModel> = emptyList(),
)

@Serializable
internal data class StoredModel(
    val modelId: String,
    val modelName: String,
    // 旧版本 blob 可能缺 source 字段（Gson 静默留 null），给默认值以免 kotlinx 抛缺字段异常。
    val source: StoredModelSource = StoredModelSource.REMOTE,
    val isStt: Boolean = false,
    val isTts: Boolean = false,
    val capabilities: MultimodalCapabilities = MultimodalCapabilities(),
)
