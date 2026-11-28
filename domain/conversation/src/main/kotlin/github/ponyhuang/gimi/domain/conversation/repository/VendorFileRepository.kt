package github.ponyhuang.gimi.domain.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * 一条已上传到厂商文件服务的注册记录。
 *
 * 注册表在 chip 上传成功时立即记录（无论草稿随后是否发送），供聊天抽屉的"上传的文件"
 * 列表按厂商查询复用。fileId 是厂商服务器侧的唯一真源：本地不存文件字节、不做内容
 * 去重——同内容在多个会话各传一次。注册表同时维护 [fileId] 与本地归档副本的绑定
 * （[localPath]）：发送归档与下载兜底成功时回写，供历史还原与"点击打开附件"先找
 * 本地、找不到再走厂商下载。
 *
 * @property fileId 厂商 Files API 签发的引用 ID。
 * @property vendor 厂商模板 ID（如 `deepseek`）；列表按当前会话厂商过滤。
 * @property displayName 上传时的文件名；无本地副本时名字是唯一可展示内容。
 * @property mimeType 上传时的 MIME 类型；抽屉回填 chip 据此恢复类别与图标。
 * @property bytes 厂商响应的文件大小。
 * @property createdAtEpochSeconds 厂商响应的创建时间（Unix 秒）。
 * @property localPath 绑定的本地归档副本绝对路径；null 表示尚无绑定或绑定已失效。
 */
data class UploadedFileEntry(
    val fileId: String,
    val vendor: String,
    val displayName: String,
    val mimeType: String,
    val bytes: Long,
    val createdAtEpochSeconds: Long,
    val localPath: String? = null,
)

/**
 * 已上传文件的本地注册表；抽屉复用列表的数据源，兼管 fileId 与本地归档副本的绑定。
 */
interface UploadedFileRegistry {
    /** 上传成功即记录；同一 fileId 重复记录按幂等处理。 */
    suspend fun record(entry: UploadedFileEntry)

    /** 按厂商查询全部记录，创建时间倒序。 */
    fun entriesFor(vendor: String): Flow<List<UploadedFileEntry>>

    /** 查询 [fileId] 绑定的本地归档副本路径；无记录或无绑定时返回 null。 */
    suspend fun localPathFor(vendor: String, fileId: String): String?

    /** 回写 [fileId] 与本地归档副本的绑定；记录不存在时静默幂等。 */
    suspend fun bindLocalFile(vendor: String, fileId: String, localPath: String)

    /** 厂商侧文件被删除后移除本地记录；不存在时静默幂等。 */
    suspend fun remove(vendor: String, fileId: String)
}

/** 上传请求：实现方从 [filePath] 读取字节并按 mime 映射厂商 purpose。 */
data class VendorFileUpload(
    val mimeType: String,
    val displayName: String,
    val filePath: String,
    /**
     * 厂商 purpose 覆盖值（如 OpenAI 的 `batch`/`fine-tune`/`user_data`）。
     * 为空时实现按 mime 与厂商走默认映射；Claude 等无 purpose 概念的协议忽略该字段。
     */
    val purposeHint: String? = null,
)

/**
 * 失败原因，不携带界面文案；被文件网关的全部结果类型共享。
 */
enum class VendorFileFailureReason { TIMEOUT, TOO_LARGE, NETWORK, VENDOR_ERROR, UNSUPPORTED_VENDOR, NOT_FOUND }

/** 上传结果：fileId 直接可用，或带原因的失败（不静默降级）。 */
sealed interface VendorFileUploadResult {
    /** 上传成功；[bytes]/[createdAtEpochSeconds] 取自厂商响应。 */
    data class Uploaded(
        val fileId: String,
        val bytes: Long,
        val createdAtEpochSeconds: Long,
    ) : VendorFileUploadResult

    data class Failure(val reason: VendorFileFailureReason, val message: String? = null) : VendorFileUploadResult
}

/**
 * 厂商无关的文件元数据（各协议 FileObject 的交集字段）。
 *
 * 厂商特有字段（如 Claude 的 `scope`/`downloadable`、OpenAI 的 `purpose`）不进模型，
 * 原样保留在 [raw] 供调用方按需取用——统一 DTO 不丢信息，也不把厂商私有枚举泄漏到上层。
 *
 * @property id 厂商 Files API 的文件 ID。
 * @property filename 文件名；厂商未返回时为 null。
 * @property sizeBytes 文件大小（字节）；未知时为 null。
 * @property mimeType MIME 类型；协议未提供时为 null。
 * @property createdAtEpochSeconds 创建时间（Unix 秒）；未知时为 0。
 * @property expiresAtEpochSeconds 过期时间（Unix 秒）；厂商无过期概念或不返回时为 null。
 * @property raw 厂商原始响应 JSON，字段名与各家官方文档一致。
 */
data class VendorFileObject(
    val id: String,
    val filename: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val createdAtEpochSeconds: Long = 0L,
    val expiresAtEpochSeconds: Long? = null,
    val raw: JsonObject? = null,
)

/** 列出文件结果；[Success.nextCursor] 是不透明游标，原样回传即可翻页。 */
sealed interface VendorFileListResult {
    data class Success(
        val files: List<VendorFileObject>,
        val nextCursor: String? = null,
    ) : VendorFileListResult

    data class Failure(val reason: VendorFileFailureReason, val message: String? = null) : VendorFileListResult
}

/** 查询单个文件元数据结果。 */
sealed interface VendorFileObjectResult {
    data class Success(val file: VendorFileObject) : VendorFileObjectResult
    data class Failure(val reason: VendorFileFailureReason, val message: String? = null) : VendorFileObjectResult
}

/** 删除文件结果；[deleted] 取自厂商响应语义。 */
sealed interface VendorFileDeleteResult {
    data class Success(val fileId: String, val deleted: Boolean) : VendorFileDeleteResult
    data class Failure(val reason: VendorFileFailureReason, val message: String? = null) : VendorFileDeleteResult
}

/** 下载文件内容结果；[Success.localPath] 是实现落盘的临时文件，由调用方负责消费后释放。 */
sealed interface VendorFileContentResult {
    data class Success(
        val fileId: String,
        val localPath: String,
        val mimeType: String? = null,
        val bytes: Long,
    ) : VendorFileContentResult

    data class Failure(val reason: VendorFileFailureReason, val message: String? = null) : VendorFileContentResult
}

/**
 * 厂商文件网关：对厂商 Files API 的统一五能力操作（上传/列出/查询/删除/下载内容）。
 *
 * purpose 映射（如 DeepSeek 的 rich_layout_recognition / user_data、OpenAI 的六枚举）
 * 是协议实现方的私有细节；调用方提供当前实例对话连接的协议、端点与生效凭据。
 * 兼容 OpenAI/Claude 协议的三方厂商复用对应协议实现，仅 endpoint 不同。
 */
interface VendorFileGateway {
    /**
     * 上传 [request] 指向的本地文件。
     *
     * @param vendor 厂商模板 ID；用于 purpose 等厂商特定参数。
     * @param protocol 当前对话连接的生效协议；未实现的协议返回
     *   [VendorFileFailureReason.UNSUPPORTED_VENDOR]。
     * @param endpoint 厂商 API 基地址（实例对话连接端点，已 trimEnd('/')）。
     * @param apiKey 该连接的生效凭据。
     */
    suspend fun upload(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        request: VendorFileUpload,
    ): VendorFileUploadResult

    /**
     * 列出厂商文件；[cursor] 为上次响应的 [VendorFileListResult.Success.nextCursor]，
     * 为 null 时从第一页开始。
     */
    suspend fun listFiles(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        limit: Int? = null,
        cursor: String? = null,
    ): VendorFileListResult

    /** 查询单个文件元数据；文件不存在返回 [VendorFileFailureReason.NOT_FOUND]。 */
    suspend fun getFile(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileObjectResult

    /** 删除厂商侧文件；删除成功后本地注册表记录同步移除。 */
    suspend fun deleteFile(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileDeleteResult

    /** 下载文件内容到实现方临时目录并返回路径；文件不存在返回 [VendorFileFailureReason.NOT_FOUND]。 */
    suspend fun getContent(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileContentResult
}

/**
 * 为厂商引用附件解析"可打开的本地载荷"的结果。
 */
sealed interface VendorAttachmentResolution {
    /** 解析成功；[attachment] 携带可读的 [FileAttachment.payloadReference] 本地副本。 */
    data class Resolved(val attachment: FileAttachment) : VendorAttachmentResolution

    /** 解析失败；[reason] 区分厂商不支持下载与网络等临时失败，供 UI 选择文案。 */
    data class Failed(val reason: VendorFileFailureReason) : VendorAttachmentResolution
}

/**
 * 为厂商引用附件（只有 fileId、无本地副本）寻找或重建可打开的本地载荷。
 *
 * 解析顺序即打开策略：先查注册表的本地归档绑定（离线秒开），绑定缺失或悬空时走
 * 厂商下载兜底，下载成功后把副本归档进共享工作区并回写绑定，下次打开不再走网络。
 * 文档附件的点击打开链路使用本契约；音频播放保持提示语义，不接下载兜底。
 */
interface VendorAttachmentResolver {
    /**
     * 解析 [attachment] 指向的本地载荷。
     *
     * @param attachment 厂商引用附件（[FileAttachment.fileId] 非空）；其他形态附件
     *   自带本地载荷，不应进入本契约。
     * @param connection 当前实例的对话连接（协议/端点/凭据）；null 表示连接不可用，
     *   只能依赖既有绑定，返回 [VendorAttachmentResolution.Failed]。
     */
    suspend fun resolveVendorPayload(
        attachment: FileAttachment,
        connection: ResolvedServiceConnection?,
    ): VendorAttachmentResolution
}
