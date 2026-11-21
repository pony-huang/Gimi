package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.data.conversation.local.UploadedFileDao
import github.ponyhuang.gimi.data.conversation.local.UploadedFileEntity
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [UploadedFileRegistry] 的 Room 实现。
 *
 * 记录在 chip 上传成功时写入（无论草稿随后是否发送）；fileId 主键幂等覆盖同名重传。
 * 本地只存展示元数据（名字/大小/时间），不存文件字节也不缓存 file_id 映射之外的派生状态。
 */
@Singleton
class DefaultUploadedFileRegistry @Inject constructor(
    private val dao: UploadedFileDao,
) : UploadedFileRegistry {

    override suspend fun record(entry: UploadedFileEntry) {
        dao.upsert(
            UploadedFileEntity(
                fileId = entry.fileId,
                vendor = entry.vendor,
                displayName = entry.displayName,
                mimeType = entry.mimeType,
                bytes = entry.bytes,
                createdAtEpochSeconds = entry.createdAtEpochSeconds,
            ),
        )
    }

    override fun entriesFor(vendor: String): Flow<List<UploadedFileEntry>> =
        dao.observeForVendor(vendor).map { entities ->
            entities.map { entity ->
                UploadedFileEntry(
                    fileId = entity.fileId,
                    vendor = entity.vendor,
                    displayName = entity.displayName,
                    mimeType = entity.mimeType,
                    bytes = entity.bytes,
                    createdAtEpochSeconds = entity.createdAtEpochSeconds,
                )
            }
        }

    override suspend fun remove(vendor: String, fileId: String) {
        dao.delete(fileId = fileId, vendor = vendor)
    }
}
