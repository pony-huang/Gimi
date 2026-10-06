package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.usecase.ChatAttachmentValidationFailure

/** 将领域校验原因转换为 Route 使用的现有提示类型。 */
internal fun ChatAttachmentValidationFailure.toChatNotice(): ChatNotice = when (this) {
    ChatAttachmentValidationFailure.MixedCategories -> ChatNotice.MixedAttachmentCategories
    ChatAttachmentValidationFailure.ModelUnavailable -> ChatNotice.ChatModelUnavailable
    ChatAttachmentValidationFailure.CategoryUnsupported -> ChatNotice.AttachmentCategoryUnsupported
    is ChatAttachmentValidationFailure.UnsupportedOrTooLarge -> ChatNotice.AttachmentUnsupportedOrTooLarge(displayName)
    ChatAttachmentValidationFailure.DocumentTotalSizeLimitExceeded -> ChatNotice.DocumentTotalSizeLimitExceeded
}
