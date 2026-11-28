package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.assumedVision
import javax.inject.Inject

/** 附件不能进入当前发送请求的原因，不持有界面文案。 */
sealed interface ChatAttachmentValidationFailure {
    /** 一次请求不能混合不同附件类别。 */
    data object MixedCategories : ChatAttachmentValidationFailure

    /** 当前选择的模型已不在模型目录中。 */
    data object ModelUnavailable : ChatAttachmentValidationFailure

    /** 当前模型不支持该附件类别。 */
    data object CategoryUnsupported : ChatAttachmentValidationFailure

    /** 文件 MIME 或大小超出模型能力；displayName 用于提示定位该文件。 */
    data class UnsupportedOrTooLarge(val displayName: String) : ChatAttachmentValidationFailure

    /** 本次文档请求的总大小超过限制。 */
    data object DocumentTotalSizeLimitExceeded : ChatAttachmentValidationFailure

    /** 草稿携带的厂商 file_id 与当前模型厂商不一致；displayName 用于提示定位该文件。 */
    data class VendorMismatch(val displayName: String) : ChatAttachmentValidationFailure
}

/** 发送前校验附件组合、模型能力与文档请求总量，不进行文件 IO。 */
class ValidateChatAttachmentsUseCase @Inject constructor() {
    operator fun invoke(
        selection: ModelSelection,
        instances: List<ModelServiceInstance>,
        drafts: List<DraftAttachment>,
    ): ChatAttachmentValidationFailure? {
        if (drafts.isEmpty()) return null
        if (drafts.mapTo(hashSetOf()) { it.category }.size != 1) {
            return ChatAttachmentValidationFailure.MixedCategories
        }
        val model = instances.firstOrNull { it.id == selection.instanceId }
            ?.findModel(selection)
            ?: return ChatAttachmentValidationFailure.ModelUnavailable
        // 草稿携带的厂商 file_id 只能被同一厂商的连接消费；切换厂商后必须重传或移除，
        // 否则适配器会把外厂 file_id 当作自有 file 块发出，厂商侧必然报错。
        val vendor = instances.firstOrNull { it.id == selection.instanceId }?.templateId
        val mismatched = drafts.firstOrNull { it.fileVendor != null && it.fileVendor != vendor }
        if (mismatched != null) {
            return ChatAttachmentValidationFailure.VendorMismatch(mismatched.displayName)
        }
        val (supportedMimeTypes, perFileLimit, skipsInlineTotalLimit) = when (drafts.first().category) {
            // 视觉能力未声明时按默认内联图像假定放行，不因目录缺元数据拒绝图片。
            AttachmentCategory.IMAGE -> model.multimodal.assumedVision.let {
                Triple(it.supportedMimeTypes, it.maxInlineBytes, false)
            }
            AttachmentCategory.AUDIO -> model.multimodal.audioInput?.let {
                Triple(it.supportedMimeTypes, it.maxInlineBytes, false)
            }
            AttachmentCategory.DOCUMENT -> model.multimodal.documentInput?.let { document ->
                when (document.ingestMode) {
                    // 路径文本模式不随请求传输内容：仅做 MIME 过滤，大小限制交给读取工具。
                    FileIngestMode.PATH_TEXT -> Triple(document.supportedMimeTypes, null, true)
                    // FILE_REFERENCE 模式走厂商上传限额（maxUploadBytes），内联总量限制不适用。
                    FileIngestMode.FILE_REFERENCE ->
                        Triple(document.supportedMimeTypes, document.maxUploadBytes, true)
                    FileIngestMode.INLINE, FileIngestMode.NOT_SUPPORTED ->
                        Triple(document.supportedMimeTypes, document.maxInlineBytes, false)
                }
            }
        } ?: return ChatAttachmentValidationFailure.CategoryUnsupported
        val unsupported = drafts.firstOrNull {
            it.mimeType !in supportedMimeTypes ||
                perFileLimit?.let { limit -> it.sizeBytes > limit } == true
        }
        if (unsupported != null) {
            return ChatAttachmentValidationFailure.UnsupportedOrTooLarge(unsupported.displayName)
        }
        if (!skipsInlineTotalLimit &&
            drafts.first().category == AttachmentCategory.DOCUMENT &&
            drafts.sumOf(DraftAttachment::sizeBytes) > MAX_DOCUMENT_REQUEST_BYTES
        ) {
            return ChatAttachmentValidationFailure.DocumentTotalSizeLimitExceeded
        }
        return null
    }

    private companion object {
        const val MAX_DOCUMENT_REQUEST_BYTES: Long = 50L * 1024 * 1024
    }
}
