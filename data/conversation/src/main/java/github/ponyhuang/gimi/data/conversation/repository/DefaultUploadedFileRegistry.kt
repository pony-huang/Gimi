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
 * 记录在 chip 上传成功时写入（无论草稿随后是否发送）；fileId 主键幂等覆盖重传。
 * 本地存展示元数据与归档绑定（localPath）：绑定在上传时以草稿路径占位，发送归档或
 * 下载兜底成功后回写为工作区路径，供历史还原与"点击打开附件"先找本地。
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
                localPath = entry.localPath,
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
                    localPath = entity.localPath,
                )
            }
        }

    override suspend fun localPathFor(vendor: String, fileId: String): String? =
        dao.localPath(fileId = fileId, vendor = vendor)

    override suspend fun bindLocalFile(vendor: String, fileId: String, localPath: String) {
        dao.bindLocalPath(fileId = fileId, vendor = vendor, localPath = localPath)
    }

    override suspend fun remove(vendor: String, fileId: String) {
        dao.delete(fileId = fileId, vendor = vendor)
    }
}
