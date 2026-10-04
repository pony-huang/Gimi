package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
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
}

/** 发送前校验附件组合、模型能力与文档请求总量，不进行文件 IO。 */
class ValidateChatAttachmentsUseCase @Inject constructor() {
    operator fun invoke(
        selection: ModelSelection,
        services: List<LLMModelSetting>,
        drafts: List<DraftAttachment>,
    ): ChatAttachmentValidationFailure? {
        if (drafts.isEmpty()) return null
        if (drafts.mapTo(hashSetOf()) { it.category }.size != 1) {
            return ChatAttachmentValidationFailure.MixedCategories
        }
        val model = services.firstOrNull { it.id == selection.serviceId }
            ?.groups?.firstOrNull { it.id == selection.groupId }
            ?.models?.firstOrNull { it.id == selection.modelId }
            ?: return ChatAttachmentValidationFailure.ModelUnavailable
        val (supportedMimeTypes, maxInlineBytes) = when (drafts.first().category) {
            AttachmentCategory.IMAGE -> model.capabilities.vision?.let {
                it.supportedMimeTypes to it.maxInlineBytes
            }
            AttachmentCategory.AUDIO -> model.capabilities.audioInput?.let {
                it.supportedMimeTypes to it.maxInlineBytes
            }
            AttachmentCategory.DOCUMENT -> model.capabilities.documentInput?.let {
                it.supportedMimeTypes to it.maxInlineBytes
            }
        } ?: return ChatAttachmentValidationFailure.CategoryUnsupported
        val unsupported = drafts.firstOrNull {
            it.mimeType !in supportedMimeTypes ||
                maxInlineBytes?.let { limit -> it.sizeBytes > limit } == true
        }
        if (unsupported != null) {
            return ChatAttachmentValidationFailure.UnsupportedOrTooLarge(unsupported.displayName)
        }
        if (drafts.first().category == AttachmentCategory.DOCUMENT &&
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
