package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObject
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** 强制刷新「上传的文件」列表的结果。 */
sealed interface UploadedFilesRefreshResult {
    /** 对账完成；[added]/[removed] 是本次写入/移除的注册表记录数。 */
    data class Synced(val added: Int, val removed: Int) : UploadedFilesRefreshResult

    /** 厂商列表拉取失败；本地记录保持不动，用户可重试。 */
    data class Failed(val reason: VendorFileFailureReason) : UploadedFilesRefreshResult
}

/**
 * 强制刷新「上传的文件」列表：仅由用户点击刷新按钮触发，从厂商 listFiles 全量翻页
 * 拉取当前厂商的文件并与本地注册表对账写回。
 *
 * 对账规则（fileId 是厂商服务器侧的唯一真源）：
 * - 厂商有、本地无 → 写入注册表（localPath 为空；mime 厂商列表不返回，按显示名扩展名
 *   推断，推断不出类别的条目跳过不收，避免复用挂载与图标渲染失败）；
 * - 双方都有 → 本地条目保留不动：上传时记录的 mime/大小/时间是真实值且携带本地绑定，
 *   比厂商列表元数据更丰富；
 * - 本地有、厂商没有（过期或别处已删）→ 移除本地记录，否则会留成删除必败的死条目。
 *
 * 列表渲染仍以注册表为唯一数据源：写回后 Room Flow 自动驱动 UI，抽屉打开零请求。
 * 失败即中止：任何一页拉取失败都不做对账，本地记录保持不动。
 */
class RefreshUploadedFilesUseCase @Inject constructor(
    private val gateway: VendorFileGateway,
    private val registry: UploadedFileRegistry,
) {
    suspend operator fun invoke(
        vendor: String,
        connection: ResolvedServiceConnection,
    ): UploadedFilesRefreshResult {
        // 全量翻页：对账需要完整集合，缺页会把本地记录误判成厂商已删。
        val files = mutableListOf<VendorFileObject>()
        var cursor: String? = null
        while (true) {
            when (val page = gateway.listFiles(
                vendor = vendor,
                protocol = connection.protocol,
                endpoint = connection.endpoint.trimEnd('/'),
                apiKey = connection.apiKey,
                limit = PAGE_LIMIT,
                cursor = cursor,
            )) {
                is VendorFileListResult.Success -> {
                    files += page.files
                    cursor = page.nextCursor
                    if (cursor == null) break
                }
                is VendorFileListResult.Failure ->
                    return UploadedFilesRefreshResult.Failed(page.reason)
            }
        }
        val local = registry.entriesFor(vendor).first().associateBy(UploadedFileEntry::fileId)
        var added = 0
        var removed = 0
        local.values.forEach { entry ->
            if (files.none { it.id == entry.fileId }) {
                registry.remove(vendor, entry.fileId)
                removed++
            }
        }
        files.forEach { file ->
            if (local.containsKey(file.id)) return@forEach
            // 列表响应不带 mime：按扩展名推断，推断不出类别的文件不收。
            val mimeType = AttachmentCategory.inferMimeType(file.filename.orEmpty()) ?: return@forEach
            registry.record(
                UploadedFileEntry(
                    fileId = file.id,
                    vendor = vendor,
                    displayName = file.filename?.takeIf(String::isNotBlank) ?: file.id,
                    mimeType = mimeType,
                    bytes = file.sizeBytes ?: 0L,
                    createdAtEpochSeconds = file.createdAtEpochSeconds,
                    localPath = null,
                ),
            )
            added++
        }
        return UploadedFilesRefreshResult.Synced(added = added, removed = removed)
    }

    private companion object {
        /** 单页上限；对账按游标翻页拉全量，上限只控制单次请求体积。 */
        const val PAGE_LIMIT = 100
    }
}
