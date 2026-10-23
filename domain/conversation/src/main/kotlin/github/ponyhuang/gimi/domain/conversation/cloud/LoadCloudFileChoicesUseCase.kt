package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import javax.inject.Inject

enum class CloudFileAvailability { Ready, ReuploadRequired, SourceUnavailable, ReferenceUnverified }

/** 云文件选择项携带真实可用性，界面不根据“上传成功”推断可发送。 */
data class CloudFileChoice(val file: CloudFile, val availability: CloudFileAvailability, val remainingMillis: Long = 0) {
    val isSelectable: Boolean get() = availability == CloudFileAvailability.Ready ||
        availability == CloudFileAvailability.ReuploadRequired

    fun toDraft(): DraftAttachment? {
        if (!isSelectable) return null
        val category = AttachmentCategory.from(file.mimeType, file.displayName) ?: return null
        return DraftAttachment(file.localReference.orEmpty(), file.displayName, file.mimeType, file.sizeBytes,
            category, CloudAttachmentReference(file.id, file.scope))
    }
}

/** 列表严格来自当前连接的应用记录；只为已验证格式检查过期文件的本地来源。 */
class LoadCloudFileChoicesUseCase @Inject constructor(
    private val repository: CloudFileRepository,
    private val support: CloudFileChatSupport,
) {
    suspend operator fun invoke(service: LLMModelSetting, modelId: String, nowMillis: Long): List<CloudFileChoice> {
        if (!service.isEnabled || !service.usesCloudFileUpload() || service.apiKey.isBlank()) return emptyList()
        return repository.list(service).map { file ->
            val availability = when {
                !support.supports(service, modelId, file.mimeType) ||
                    AttachmentCategory.from(file.mimeType, file.displayName) == null -> CloudFileAvailability.ReferenceUnverified
                file.isReusable(nowMillis) -> CloudFileAvailability.Ready
                repository.canReupload(service, file.id) -> CloudFileAvailability.ReuploadRequired
                else -> CloudFileAvailability.SourceUnavailable
            }
            CloudFileChoice(file, availability, maxOf(0L, file.expiresAtMillis - nowMillis))
        }
    }
}
