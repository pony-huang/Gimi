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
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VendorFileGateway] 的默认实现：按连接显式声明的协议选择 [FileProtocolClient]。
 *
 * - 协议 → 客户端来自 Hilt 多绑定的客户端集合，新增协议族只需新增实现类 + 一处绑定；
 * - 未实现的协议统一返回 UNSUPPORTED_VENDOR，不发网络请求；
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
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        request: VendorFileUpload,
    ): VendorFileUploadResult {
        val client = resolve(protocol)
            ?: return VendorFileUploadResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "protocol=$protocol")
        return client.upload(vendor, endpoint, apiKey, request)
    }

    override suspend fun listFiles(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        limit: Int?,
        cursor: String?,
    ): VendorFileListResult {
        val client = resolve(protocol)
            ?: return VendorFileListResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "protocol=$protocol")
        return client.listFiles(vendor, endpoint, apiKey, limit, cursor)
    }

    override suspend fun getFile(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileObjectResult {
        val client = resolve(protocol)
            ?: return VendorFileObjectResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "protocol=$protocol")
        return client.getFile(vendor, endpoint, apiKey, fileId)
    }

    override suspend fun deleteFile(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileDeleteResult {
        val client = resolve(protocol)
            ?: return VendorFileDeleteResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "protocol=$protocol")
        val result = client.deleteFile(vendor, endpoint, apiKey, fileId)
        if (result is VendorFileDeleteResult.Success && result.deleted) {
            registry.remove(vendor, fileId)
        }
        return result
    }

    override suspend fun getContent(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileContentResult {
        // 厂商没有 content 下载端点时不发请求，直接归 UNSUPPORTED_VENDOR（与协议族不支持同义），
        // 让打开链路弹准确的"厂商文件不支持下载"而非"下载失败"。
        if (!FileClientSupport.supportsContentDownload(vendor)) {
            return VendorFileContentResult.Failure(
                VendorFileFailureReason.UNSUPPORTED_VENDOR,
                "vendor $vendor has no content download endpoint",
            )
        }
        val client = resolve(protocol)
            ?: return VendorFileContentResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR, "protocol=$protocol")
        return client.getContent(vendor, endpoint, apiKey, fileId)
    }

    /** 连接协议决定文件协议；端点由配置拥有，客户端只拼接协议规定的路径。 */
    private fun resolve(protocol: ApiProtocol): FileProtocolClient? {
        val fileProtocol = when (protocol) {
            ApiProtocol.OpenaiChatcompletions -> FileProtocol.OPENAI_COMPATIBLE
            ApiProtocol.Anthropic -> FileProtocol.ANTHROPIC
            ApiProtocol.Gemini -> return null
        }
        return clientsByProtocol[fileProtocol]
    }
}
