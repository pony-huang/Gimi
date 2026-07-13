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
                provider.modelGroups.map { group ->
                    StoredModelGroup(
                        groupId = group.groupId,
                        groupName = group.groupName,
                        isExpanded = group.isExpanded,
                        models = group.models.map {
                            StoredModel(it.modelId, it.modelName, StoredModelSource.REMOTE)
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

internal suspend fun seedModelCatalogIfEmpty(
    database: ModelServiceDatabase,
    gson: Gson = Gson(),
) {
    database.withTransaction {
        val dao = database.modelServiceDao()
        if (dao.count() == 0) dao.upsertAll(defaultModelServiceEntities(gson))
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
)
