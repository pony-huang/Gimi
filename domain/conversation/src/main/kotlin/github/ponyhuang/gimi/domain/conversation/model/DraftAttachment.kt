package github.ponyhuang.gimi.domain.conversation.model

import kotlinx.serialization.Serializable

/**
 * Lightweight reference to an attachment copied into application-owned draft storage.
 *
 * Payload bytes are deliberately excluded so Compose saved state and Room rows stay small.
 */
@Serializable
data class DraftAttachment(
    val reference: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: AttachmentCategory,
    /** 云文件只持有共享记录的引用；移除草稿不能删除该来源或远端文件。 */
    val cloudReference: CloudAttachmentReference? = null,
)

/** 文件 ID 必须与连接摘要一起校验，禁止跨服务或凭据复用。 */
@Serializable
data class CloudAttachmentReference(val fileId: String, val scope: String)
