package github.ponyhuang.gimi.domain.conversation.model

/**
 * Lightweight reference to an attachment copied into application-owned draft storage.
 *
 * Payload bytes are deliberately excluded so Compose saved state and Room rows stay small.
 */
data class DraftAttachment(
    val reference: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: AttachmentCategory,
)
