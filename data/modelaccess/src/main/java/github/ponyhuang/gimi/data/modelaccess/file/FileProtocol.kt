package github.ponyhuang.gimi.data.modelaccess.file

import github.ponyhuang.gimi.domain.conversation.repository.VendorFileContentResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObjectResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult

/**
 * 厂商 Files API 的协议族。
 *
 * 同一协议族内的厂商（官方或三方兼容实现）共享同一个 [FileProtocolClient]，
 * 差异只有 endpoint 与凭据——新增兼容厂商零代码改动，只需在 [VendorFileProtocols]
 * 登记一行 vendor → 协议映射。
 */
enum class FileProtocol {
    /** OpenAI Files 协议：multipart + purpose 必填，`after` 游标分页，Bearer 认证。 */
    OPENAI_COMPATIBLE,

    /** Anthropic Files 协议：multipart 无 purpose，`page` 游标分页，`x-api-key` 认证。 */
    ANTHROPIC,
}

/**
 * 单个协议族的 Files API 客户端：实现该协议族的全部五个能力。
 *
 * endpoint 约定与聊天协议一致：调用方传厂商 API 基地址（可含 `/v1` 路径），
 * 客户端负责拼接协议自身的 Files 路径。
 */
interface FileProtocolClient {
    /** 本客户端实现的协议族；网关据此分发。 */
    val protocol: FileProtocol

    suspend fun upload(
        vendor: String,
        endpoint: String,
        apiKey: String,
        request: VendorFileUpload,
    ): VendorFileUploadResult

    suspend fun listFiles(
        vendor: String,
        endpoint: String,
        apiKey: String,
        limit: Int?,
        cursor: String?,
    ): VendorFileListResult

    suspend fun getFile(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileObjectResult

    suspend fun deleteFile(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileDeleteResult

    suspend fun getContent(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileContentResult
}

/**
 * vendor → 协议族登记表。
 *
 * 厂商是否提供 Files 能力由能力开关（FILE_REFERENCE）决定；这里只回答"用哪套协议"。
 * 新增兼容 OpenAI/Claude 协议的厂商时在 [byVendor] 加一行即可，无需改客户端代码。
 */
object VendorFileProtocols {
    val byVendor: Map<String, FileProtocol> = mapOf(
        "deepseek" to FileProtocol.OPENAI_COMPATIBLE,
        "openai" to FileProtocol.OPENAI_COMPATIBLE,
        "anthropic" to FileProtocol.ANTHROPIC,
    )
}
