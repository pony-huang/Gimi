package github.ponyhuang.gimi.data.modelaccess.file

import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileContentResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObjectResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VendorFileGateway] 的默认实现：vendor → 协议族 → [FileProtocolClient] 的两级分发。
 *
 * - vendor → 协议映射来自 [VendorFileProtocols]（兼容厂商零代码登记）；
 * - 协议 → 客户端来自 Hilt 多绑定的客户端集合，新增协议族只需新增实现类 + 一处绑定；
 * - 未登记的 vendor / 缺客户端的协议统一返回 UNSUPPORTED_VENDOR，不发网络请求；
 * - 删除成功后同步移除本地注册表记录，避免抽屉出现指向已删厂商文件的幽灵条目。
 */
@Singleton
class DefaultVendorFileGateway @Inject constructor(
    clients: Set<@JvmSuppressWildcards FileProtocolClient>,
    private val registry: UploadedFileRegistry,
) : VendorFileGateway {

    private val clientsByProtocol: Map<FileProtocol, FileProtocolClient> =
        clients.associateBy { it.protocol }

    override suspend fun upload(
        vendor: String,
        endpoint: String,
        apiKey: String,
        request: VendorFileUpload,
    ): VendorFileUploadResult {
        val client = resolve(vendor)
            ?: return VendorFileUploadResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "vendor=$vendor")
        return client.upload(vendor, endpoint, apiKey, request)
    }

    override suspend fun listFiles(
        vendor: String,
        endpoint: String,
        apiKey: String,
        limit: Int?,
        cursor: String?,
    ): VendorFileListResult {
        val client = resolve(vendor)
            ?: return VendorFileListResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "vendor=$vendor")
        return client.listFiles(vendor, endpoint, apiKey, limit, cursor)
    }

    override suspend fun getFile(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileObjectResult {
        val client = resolve(vendor)
            ?: return VendorFileObjectResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "vendor=$vendor")
        return client.getFile(vendor, endpoint, apiKey, fileId)
    }

    override suspend fun deleteFile(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileDeleteResult {
        val client = resolve(vendor)
            ?: return VendorFileDeleteResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "vendor=$vendor")
        val result = client.deleteFile(vendor, endpoint, apiKey, fileId)
        if (result is VendorFileDeleteResult.Success && result.deleted) {
            registry.remove(vendor, fileId)
        }
        return result
    }

    override suspend fun getContent(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileContentResult {
        val client = resolve(vendor)
            ?: return VendorFileContentResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "vendor=$vendor")
        return client.getContent(vendor, endpoint, apiKey, fileId)
    }

    /** vendor → 协议 → 客户端；任一环节缺失返回 null（调用方归为 UNSUPPORTED_VENDOR）。 */
    private fun resolve(vendor: String): FileProtocolClient? {
        val protocol = VendorFileProtocols.byVendor[vendor] ?: return null
        return clientsByProtocol[protocol]
    }
}
