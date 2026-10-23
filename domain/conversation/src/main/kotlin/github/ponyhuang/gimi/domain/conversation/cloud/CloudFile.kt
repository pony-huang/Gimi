package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** 本应用上传的共享记录；scope 是连接作用域摘要，不保存 API 密钥。时间单位为毫秒。 */
@Serializable
data class CloudFile(
    val id: String,
    val scope: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentHash: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    /** 服务返回的到期时间；null 表示仅有本地复用截止时间，不声称服务端已经删除。 */
    val remoteExpiresAtMillis: Long?,
    val localReference: String?,
) {
    fun isReusable(nowMillis: Long): Boolean = nowMillis < expiresAtMillis
}

/** 一次准备过程的单文件状态；重试某文件不重建其它文件的就绪记录。 */
sealed interface CloudFilePreparation {
    /** SDK 不暴露字节级进度时使用不定进度，避免虚构上传百分比。 */
    data object Uploading : CloudFilePreparation
    /** 已持久化、可继续引用的远端记录。 */
    data class Ready(val file: CloudFile) : CloudFilePreparation
    /** 失败仅影响当前文件，UI 根据枚举提供本地化文案。 */
    data class Failed(val reason: CloudFileFailure) : CloudFilePreparation
}

enum class CloudFileFailure {
    Disabled, UnverifiedChatReference, UnsupportedProvider, InvalidConfiguration,
    LocalFileUnavailable, UploadRejected, InvalidResponse, StorageUnavailable, FileTooLarge,
}

/** 不把包含服务响应、文件路径或凭据的底层异常传到展示层。 */
class CloudFileException(val reason: CloudFileFailure) : Exception(reason.name)

interface CloudFileRepository {
    suspend fun list(service: LLMModelSetting): List<CloudFile>
    /** 优先验证并复用本地原文件，缺失时恢复云端内容；不上传、不修改聊天协议。 */
    suspend fun resolveLocal(service: LLMModelSetting, reference: CloudAttachmentReference): CloudFile =
        throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
    fun prepare(service: LLMModelSetting, attachment: DraftAttachment): Flow<CloudFilePreparation>
    fun prepareExisting(service: LLMModelSetting, fileId: String): Flow<CloudFilePreparation>
    /** 仅用于过期记录的可选性判断，不上传、不修改文件。 */
    suspend fun canReupload(service: LLMModelSetting, fileId: String): Boolean
}

/** 上传与聊天引用是两个独立能力；兼容聊天接口并不表示支持任意文件 ID。 */
interface CloudFileChatSupport {
    fun supports(service: LLMModelSetting, modelId: String, mimeType: String): Boolean
}
