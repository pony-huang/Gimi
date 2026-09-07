package github.ponyhuang.gimi.data.workfiles.repository

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** DataStore 中完整的工作目录配置，列表顺序即用户配置顺序。 */
@Serializable
internal data class StoredWorkDirectoryConfig(
    val directories: List<StoredWorkDirectory> = emptyList(),
)

/**
 * 工作目录的持久化表示。
 *
 * @property id 应用生成的稳定标识。
 * @property treeUri SAF tree URI。
 * @property displayName provider 返回的显示名。
 * @property authority DocumentsProvider authority。
 * @property enabled 是否参与搜索。
 * @property accessStatus 最近一次授权检查结果。
 * @property addedAtEpochMillis 首次加入时间。
 */
@Serializable
internal data class StoredWorkDirectory(
    val id: String,
    val treeUri: String,
    val displayName: String,
    val authority: String,
    val enabled: Boolean,
    val accessStatus: String,
    val addedAtEpochMillis: Long,
) {
    fun toDomain() = WorkDirectory(
        id = id,
        treeUri = treeUri,
        displayName = displayName,
        authority = authority,
        enabled = enabled,
        accessStatus = runCatching { WorkDirectoryAccessStatus.valueOf(accessStatus) }
            .getOrDefault(WorkDirectoryAccessStatus.PERMISSION_LOST),
        addedAtEpochMillis = addedAtEpochMillis,
    )

    companion object {
        fun fromDomain(directory: WorkDirectory) = StoredWorkDirectory(
            id = directory.id,
            treeUri = directory.treeUri,
            displayName = directory.displayName,
            authority = directory.authority,
            enabled = directory.enabled,
            accessStatus = directory.accessStatus.name,
            addedAtEpochMillis = directory.addedAtEpochMillis,
        )
    }
}

/** JSON-backed typed DataStore serializer for work-directory configuration. */
internal object WorkDirectoryConfigSerializer : Serializer<StoredWorkDirectoryConfig> {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override val defaultValue = StoredWorkDirectoryConfig()

    override suspend fun readFrom(input: InputStream): StoredWorkDirectoryConfig = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (exception: SerializationException) {
        throw CorruptionException("工作目录配置无法解析", exception)
    }

    override suspend fun writeTo(t: StoredWorkDirectoryConfig, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

/** Owns ordered structured persistence for user-selected work directories. */
class WorkDirectoryConfigStore internal constructor(
    private val dataStore: DataStore<StoredWorkDirectoryConfig>,
) {
    val directories: Flow<List<WorkDirectory>> = dataStore.data.map { config ->
        config.directories.map(StoredWorkDirectory::toDomain)
    }

    suspend fun replace(directories: List<WorkDirectory>) {
        dataStore.updateData {
            StoredWorkDirectoryConfig(directories.map(StoredWorkDirectory::fromDomain))
        }
    }

    suspend fun current(): List<WorkDirectory> = directories.first()
}
