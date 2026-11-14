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
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection
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

/**
 * 公共模板目录行；用户凭据与实例配置在 Keystore 加密快照里，重建不丢配置。
 *
 * Room 只承载静态元数据与内置候选模型目录：模板连接定义、厂商能力集成范围与
 * 内置模型清单。用户实例由 [ModelServiceConfigurationStore] 单独持久化。
 */
@Entity(tableName = "model_template")
data class ModelTemplateEntity(
    @PrimaryKey
    val templateId: String,
    val displayName: String,
    val displayOrder: Int,
    /** 模板连接定义（含内置候选目录）的序列化快照。 */
    val connectionsJson: String,
    val homepageUrl: String,
    val keyHelpUrl: String,
)

@Dao
interface ModelTemplateDao {
    @Query("SELECT * FROM model_template ORDER BY displayOrder, templateId")
    fun observeAll(): Flow<List<ModelTemplateEntity>>

    @Query("SELECT * FROM model_template ORDER BY displayOrder, templateId")
    suspend fun getAll(): List<ModelTemplateEntity>

    @Query("SELECT * FROM model_template WHERE templateId = :templateId")
    suspend fun get(templateId: String): ModelTemplateEntity?

    @Query("SELECT COUNT(*) FROM model_template")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ModelTemplateEntity>)

    @Query("DELETE FROM model_template WHERE templateId = :templateId")
    suspend fun delete(templateId: String)
}

@Database(
    entities = [ModelTemplateEntity::class],
    // v3：模板目录从"按服务种类的服务行"重建为"按厂商的模板行"；用户配置已迁往
    // 加密实例快照，目录重建不丢配置（见仓库重构约定，不写历史迁移）。
    version = 3,
    exportSchema = false,
)
abstract class ModelTemplateDatabase : RoomDatabase() {
    abstract fun modelTemplateDao(): ModelTemplateDao

    companion object {
        const val DATABASE_NAME = "model-services.db"
    }
}

internal fun modelTemplateEntities(): List<ModelTemplateEntity> =
    ModelServiceTemplates.all.mapIndexed { index, template ->
        ModelTemplateEntity(
            templateId = template.id,
            displayName = template.name,
            displayOrder = index,
            connectionsJson = modelCatalogJson.encodeToString(
                template.connections.map(::StoredTemplateConnection),
            ),
            homepageUrl = template.homepageUrl,
            keyHelpUrl = template.keyHelpUrl,
        )
    }

/**
 * 把内置清单里缺的模板增量写进 Room：新厂商在已有用户数据上也能进入添加目录，
 * 已存在的模板行不覆盖（保留已升级过的候选目录）。
 * 调用时机：每次启动仓库初始化时执行一次；幂等，安全。
 */
internal suspend fun seedMissingTemplates(database: ModelTemplateDatabase) {
    database.withTransaction {
        val dao = database.modelTemplateDao()
        val existingIds = dao.getAll().mapTo(HashSet()) { it.templateId }
        val missing = modelTemplateEntities().filterNot { it.templateId in existingIds }
        if (missing.isNotEmpty()) dao.upsertAll(missing)
    }
}

/**
 * 删除内置清单里已不存在的模板行（例如厂商下线）。目录行只存公共模板，
 * 删除不丢用户配置；不清理的话旧行会残留在添加目录里。
 * 调用时机：每次启动时执行一次；幂等，安全。
 */
internal suspend fun removeRemovedTemplates(database: ModelTemplateDatabase) {
    database.withTransaction {
        val dao = database.modelTemplateDao()
        val builtInIds = modelTemplateEntities().mapTo(HashSet()) { it.templateId }
        dao.getAll()
            .filterNot { it.templateId in builtInIds }
            .forEach { dao.delete(it.templateId) }
    }
}

/**
 * 把内置候选模型目录合并进已存在的模板行：应用更新带来的新模型对已有安装生效，
 * 且不覆盖用户派生实例的选择（实例选择在加密快照里，天然不受影响）。
 * 合并在读取期完成（见 [decodeTemplateConnections] 与候选合并），此函数保留
 * 持久化升级路径：模板行 JSON 与内置清单不一致时写回合并结果。
 */
internal suspend fun upgradeTemplateCatalogs(database: ModelTemplateDatabase) {
    database.withTransaction {
        val dao = database.modelTemplateDao()
        val defaultsById = modelTemplateEntities().associateBy { it.templateId }
        dao.getAll().forEach { entity ->
            val default = defaultsById[entity.templateId] ?: return@forEach
            if (entity.connectionsJson != default.connectionsJson) {
                val existing = decodeTemplateConnections(entity.connectionsJson)
                val defaults = decodeTemplateConnections(default.connectionsJson)
                val merged = mergeTemplateCatalogs(existing, defaults)
                dao.upsertAll(
                    listOf(
                        entity.copy(
                            connectionsJson = modelCatalogJson.encodeToString(
                                merged.map(::StoredTemplateConnection),
                            ),
                        ),
                    ),
                )
            }
        }
    }
}

/** 持久化期的模板连接快照：能力、协议集合、默认端点与内置目录。 */
@Serializable
internal data class StoredTemplateConnection(
    val capability: String,
    val protocols: List<String>,
    val defaultEndpoints: Map<String, String>,
    val endpointPolicy: String,
    val endpointSchemes: Set<String>,
    val supportsWorkspace: Boolean = false,
    val catalog: List<StoredModel> = emptyList(),
) {
    constructor(connection: TemplateConnection) : this(
        capability = connection.capability.name,
        protocols = connection.protocols.map { it.name },
        defaultEndpoints = connection.defaultEndpoints.mapKeys { it.key.name },
        endpointPolicy = connection.endpointPolicy.name,
        endpointSchemes = connection.endpointSchemes,
        supportsWorkspace = connection.supportsWorkspace,
        catalog = connection.catalog.map(::StoredModel),
    )

    fun toDomain(): TemplateConnection? {
        val capability = ServiceCapability.entries.firstOrNull { it.name == capability } ?: return null
        val protocols = protocols.mapNotNull { name -> ApiProtocol.entries.firstOrNull { it.name == name } }
        if (protocols.isEmpty()) return null
        val endpoints = defaultEndpoints.mapNotNull { (protocol, endpoint) ->
            ApiProtocol.entries.firstOrNull { it.name == protocol }?.let { it to endpoint }
        }.toMap()
        val policy = EndpointPolicy.entries.firstOrNull { it.name == endpointPolicy } ?: EndpointPolicy.UserConfigured
        return TemplateConnection(
            capability = capability,
            protocols = protocols,
            defaultEndpoints = endpoints,
            endpointPolicy = policy,
            endpointSchemes = endpointSchemes,
            supportsWorkspace = supportsWorkspace,
            catalog = catalog.map { it.toDomain() },
        )
    }
}

internal fun decodeTemplateConnections(json: String): List<TemplateConnection> =
    runCatching {
        modelCatalogJson.decodeFromString<List<StoredTemplateConnection>>(json)
    }.onFailure {
        android.util.Log.w(TAG, "Unable to decode template connections.", it)
    }.getOrDefault(emptyList()).mapNotNull { it.toDomain() }

private const val TAG = "ModelTemplateDatabase"

/** 内置目录与既有行的候选模型合并：既有模型保留，缺失的内置模型补齐。 */
internal fun mergeTemplateCatalogs(
    existing: List<TemplateConnection>,
    defaults: List<TemplateConnection>,
): List<TemplateConnection> = existing.map { connection ->
    val default = defaults.firstOrNull { it.capability == connection.capability } ?: return@map connection
    val existingIds = connection.catalog.mapTo(HashSet()) { it.id }
    connection.copy(catalog = connection.catalog + default.catalog.filterNot { it.id in existingIds })
} + defaults.filterNot { default -> existing.any { it.capability == default.capability } }

/** Persistence-only model origin used to reconcile remote and manually entered models. */
@Serializable
internal enum class StoredModelSource {
    REMOTE,
    USER,
}

/** 实例已选模型与模板目录共用的持久化模型。 */
@Serializable
internal data class StoredModel(
    val modelId: String,
    val modelName: String,
    // 旧版本 blob 可能缺 source 字段（Gson 静默留 null），给默认值以免 kotlinx 抛缺字段异常。
    val source: StoredModelSource = StoredModelSource.REMOTE,
    /**
     * 该模型可承担的任务集合。
     *
     * 缺省视为可对话的 [ModelCapability.LLM] 模型：远端目录接口只返回模型 ID，
     * 不携带能力元数据，因此未声明能力的模型必须仍能用于聊天。
     */
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.LLM),
    /** 该模型可接收的输入模态。 */
    val multimodal: MultimodalCapabilities = MultimodalCapabilities(),
) {
    constructor(model: github.ponyhuang.gimi.domain.modelcatalog.model.Model) : this(
        modelId = model.id,
        modelName = model.name,
        capabilities = model.capabilities,
        multimodal = model.multimodal,
    )

    fun toDomain(): github.ponyhuang.gimi.domain.modelcatalog.model.Model =
        github.ponyhuang.gimi.domain.modelcatalog.model.Model(
            id = modelId,
            name = modelName,
            capabilities = capabilities,
            multimodal = multimodal,
        )
}
