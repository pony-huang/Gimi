package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 附件校验的请求边界与能力限制。 */
class ValidateChatAttachmentsUseCaseTest {
    private val validate = ValidateChatAttachmentsUseCase()
    private val selection = ModelSelection("instance", "connection", "model")
    private val model = Model("model", "Model")
    private val instance = ModelServiceInstance(
        id = "instance", name = "Service", templateId = "openai",
        isEnabled = true, isOfficialToolsEnabled = false, sharedApiKey = "key",
        connections = listOf(
            ServiceConnection(
                id = "connection",
                capability = ServiceCapability.Conversation,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://example.com",
                models = listOf(model),
            ),
        ),
    )

    @Test
    fun documentTotalExactlyAtTheLimitIsAcceptedButOneExtraByteIsRejected() {
        val half = 25L * 1024 * 1024
        val first = draft("first.pdf", half)
        val second = draft("second.pdf", half)
        assertNull(validate(selection, listOf(instance), listOf(first, second)))
        assertEquals(ChatAttachmentValidationFailure.DocumentTotalSizeLimitExceeded,
            validate(selection, listOf(instance), listOf(first, second.copy(sizeBytes = half + 1))))
    }

    @Test
    fun unsupportedMimeAndIndividualSizeIdentifyTheOffendingFile() {
        val unsupported = draft("unsupported.bin", 100).copy(mimeType = "application/octet-stream")
        val oversized = draft("oversized.pdf", 50L * 1024 * 1024 + 1)
        for (file in listOf(unsupported, oversized)) {
            assertEquals(ChatAttachmentValidationFailure.UnsupportedOrTooLarge(file.displayName),
                validate(selection, listOf(instance), listOf(file)))
        }
    }

    @Test
    fun modelWithoutDocumentCapabilityRejectsDocuments() {
        val unavailable = model.copy(multimodal = MultimodalCapabilities(documentInput = null))
        val source = instance.copy(
            connections = listOf(
                ServiceConnection(
                    id = "connection",
                    capability = ServiceCapability.Conversation,
                    protocol = ApiProtocol.OpenaiChatcompletions,
                    endpoint = "https://example.com",
                    models = listOf(unavailable),
                ),
            ),
        )
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

    private fun draft(name: String, sizeBytes: Long) = DraftAttachment(
        reference = "/drafts/$name", displayName = name, mimeType = "application/pdf",
        sizeBytes = sizeBytes, category = AttachmentCategory.DOCUMENT,
    )
}
