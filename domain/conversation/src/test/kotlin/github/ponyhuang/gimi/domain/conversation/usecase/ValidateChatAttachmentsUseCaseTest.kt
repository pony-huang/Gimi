package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 附件校验的请求边界与能力限制。 */
class ValidateChatAttachmentsUseCaseTest {
    private val validate = ValidateChatAttachmentsUseCase()
    private val selection = ModelSelection("service", "group", "model")
    private val model = Model("model", "Model")
    private val service = LLMModelSetting(
        id = "service", name = "Service", isEnabled = true, apiKey = "key",
        apiBaseUrl = "https://example.com", apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = "",
        groups = listOf(ModelGroup("group", "Group", listOf(model))),
    )

    @Test
    fun documentTotalExactlyAtTheLimitIsAcceptedButOneExtraByteIsRejected() {
        val half = 25L * 1024 * 1024
        val first = draft("first.pdf", half)
        val second = draft("second.pdf", half)
        assertNull(validate(selection, listOf(service), listOf(first, second)))
        assertEquals(ChatAttachmentValidationFailure.DocumentTotalSizeLimitExceeded,
            validate(selection, listOf(service), listOf(first, second.copy(sizeBytes = half + 1))))
    }

    @Test
    fun unsupportedMimeAndIndividualSizeIdentifyTheOffendingFile() {
        val unsupported = draft("unsupported.bin", 100).copy(mimeType = "application/octet-stream")
        val oversized = draft("oversized.pdf", 50L * 1024 * 1024 + 1)
        for (file in listOf(unsupported, oversized)) {
            assertEquals(ChatAttachmentValidationFailure.UnsupportedOrTooLarge(file.displayName),
                validate(selection, listOf(service), listOf(file)))
        }
    }

    @Test
    fun modelWithoutDocumentCapabilityRejectsDocuments() {
        val unavailable = model.copy(capabilities = MultimodalCapabilities(documentInput = null))
        val source = service.copy(groups = listOf(ModelGroup("group", "Group", listOf(unavailable))))
        assertEquals(ChatAttachmentValidationFailure.CategoryUnsupported,
            validate(selection, listOf(source), listOf(draft("a.pdf", 100))))
    }

    @Test
    fun mixedCategoriesAreRejectedBeforeResolvingTheModel() {
        val document = draft("a.pdf", 100)
        val image = document.copy(category = AttachmentCategory.IMAGE)
        assertEquals(ChatAttachmentValidationFailure.MixedCategories,
            validate(selection, emptyList(), listOf(document, image)))
    }

    @Test
    fun missingModelRejectsAttachmentsButEmptyAttachmentsNeedNoCapabilityLookup() {
        assertEquals(ChatAttachmentValidationFailure.ModelUnavailable,
            validate(selection, emptyList(), listOf(draft("a.pdf", 100))))
        assertNull(validate(selection, emptyList(), emptyList()))
    }

    @Test
    fun uploadPreferenceRejectsNewAttachmentsBeforeTheyCanFallBackToInline() {
        assertEquals(ChatAttachmentValidationFailure.CloudFileSendingUnavailable,
            validate(selection, listOf(service.copy(isFileUploadEnabled = true)), listOf(draft("a.pdf", 10))))
        assertNull(validate(selection, listOf(service.copy(isFileUploadEnabled = true)), emptyList()))
    }

    @Test
    fun restoredCloudReferenceIsNeverReadAsALocalFileEvenAfterUploadIsDisabled() {
        val file = draft("a.pdf", 10).copy(cloudReference =
            github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference("foreign", "old-scope"))
        assertEquals(ChatAttachmentValidationFailure.CloudFileSendingUnavailable,
            validate(selection, listOf(service), listOf(file)))
    }

    @Test
    fun cloudFileQuotaIsIndependentOfInlineCapabilitiesAndRejectsOverflow() {
        val cloudService = service.copy(isFileUploadEnabled = true, groups = listOf(ModelGroup("group", "Group",
            listOf(model.copy(capabilities = MultimodalCapabilities(documentInput = null))))))
        val limit = github.ponyhuang.gimi.domain.conversation.cloud.CloudFileRequestLimits.MAX_BYTES
        assertNull(validate(selection, listOf(cloudService), listOf(draft("a.pdf", limit)), prepareCloudFiles = true))
        assertEquals(ChatAttachmentValidationFailure.DocumentTotalSizeLimitExceeded,
            validate(selection, listOf(cloudService), listOf(draft("a.pdf", limit), draft("b.pdf", 1)), prepareCloudFiles = true))
        assertEquals(ChatAttachmentValidationFailure.DocumentTotalSizeLimitExceeded,
            validate(selection, listOf(cloudService), listOf(draft("a.pdf", Long.MAX_VALUE)), prepareCloudFiles = true))
    }

    @Test
    fun storedUploadPreferenceDoesNotDivertOtherProtocolsFromNativeAttachmentValidation() {
        val source = service.copy(isFileUploadEnabled = true,
            apiProtocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.Anthropic)
        assertNull(validate(selection, listOf(source), listOf(draft("a.pdf", 100))))
        val cloud = draft("a.pdf", 100).copy(cloudReference = github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference("id", "scope"))
        assertEquals(ChatAttachmentValidationFailure.CloudFileSendingUnavailable,
            validate(selection, listOf(source), listOf(cloud)))
    }

    private fun draft(name: String, sizeBytes: Long) = DraftAttachment(
        reference = "/drafts/$name", displayName = name, mimeType = "application/pdf",
        sizeBytes = sizeBytes, category = AttachmentCategory.DOCUMENT,
    )
}
