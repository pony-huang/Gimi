package github.ponyhuang.gimi.domain.conversation.model

import kotlinx.serialization.Serializable

/**
 * Lightweight reference to an attachment copied into application-owned draft storage.
 *
 * Payload bytes are deliberately excluded so Compose saved state and Room rows stay small.
 *
 * 已上传到厂商文件服务的草稿（包括从抽屉复用的 file_id-only 项）携带 [fileId]/[fileVendor]；
 * [reference] 此时为 [VendorFileReference] 私有 URI，本地没有对应草稿文件。
 */
@Serializable
data class DraftAttachment(
    val reference: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: AttachmentCategory,
    /** 厂商 Files API 签发的 file_id；未上传或上传未完成时为 null。 */
    val fileId: String? = null,
    /** [fileId] 归属的厂商模板 ID；与 [fileId] 同时设置。 */
    val fileVendor: String? = null,
) {
    /** 该草稿是否已持可直发的厂商 file 引用。 */
    val isVendorUploaded: Boolean get() = fileId != null && fileVendor != null
}
