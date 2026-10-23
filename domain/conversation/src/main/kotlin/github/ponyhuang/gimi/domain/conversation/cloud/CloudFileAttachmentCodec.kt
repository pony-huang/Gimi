package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 轻量持久化载荷，不含文件字节、密钥或下载 URL；本地路径仅用于展示。 */
@Serializable
private data class CloudFilePart(val reference: CloudAttachmentReference, val attachmentId: String,
    val sizeBytes: Long, val localReference: String?)

/** ADK FileData 使用的应用内部引用协议；仅供持久化和数据层适配，绝不发给厂商。 */
object CloudFileAttachmentCodec {
    private const val PREFIX = "gimi-cloud-file:v1:"
    fun isCloudReference(uri: String): Boolean = uri.startsWith("gimi-cloud-file:")

    fun encode(attachment: FileAttachment): String {
        val reference = requireNotNull(attachment.cloudReference)
        val value = CloudFilePart(reference, attachment.id, attachment.sizeBytes, attachment.payloadReference)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(value).toByteArray(Charsets.UTF_8))
    }

    fun decode(uri: String, mimeType: String, displayName: String): FileAttachment {
        require(uri.startsWith(PREFIX) && uri.length <= 32_768) { "Invalid cloud file reference" }
        val value = Json.decodeFromString<CloudFilePart>(Base64.getUrlDecoder().decode(uri.removePrefix(PREFIX)).toString(Charsets.UTF_8))
        require(value.reference.fileId.isNotBlank() && value.reference.scope.isNotBlank() && value.sizeBytes >= 0)
        return FileAttachment(mimeType, value.attachmentId, value.sizeBytes, displayName,
            payloadReference = value.localReference, cloudReference = value.reference)
    }
}
