package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import io.mockk.every
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** 在有副作用的上传前阻止未验证引用；上传开关不会静默修改默认协议。 */
class PrepareCloudFileUseCaseTest {
    private val repository = mockk<CloudFileRepository>()
    private val support = mockk<CloudFileChatSupport>()
    private val useCase = PrepareCloudFileUseCase(repository, support)
    private val service = LLMModelSetting("deepseek", "DeepSeek", true, apiKey = "fake-key",
        apiBaseUrl = "https://api.deepseek.com", apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "https://api.deepseek.com/anthropic", groups = emptyList())
    private val attachment = DraftAttachment("local", "a.pdf", "application/pdf", 10, AttachmentCategory.DOCUMENT)

    @Test fun disabledPreferenceDoesNotInvokeSupportOrUpload() = runBlocking {
        assertEquals(listOf(CloudFilePreparation.Failed(CloudFileFailure.Disabled)),
            useCase(service, "model", attachment).toList())
        verify(exactly = 0) { repository.prepare(any(), any()) }
        verify(exactly = 0) { support.supports(any(), any(), any()) }
    }

    @Test fun nonChatCompletionsProtocolCannotUploadOrReadCloudChoicesEvenWithPreferenceOn() = runBlocking {
        val enabled = service.copy(isFileUploadEnabled = true, apiProtocol = ApiProtocol.Anthropic)
        assertEquals(listOf(CloudFilePreparation.Failed(CloudFileFailure.InvalidConfiguration)),
            useCase(enabled, "model", attachment).toList())
        assertEquals(listOf(CloudFilePreparation.Failed(CloudFileFailure.InvalidConfiguration)),
            useCase.existing(enabled, "model", "old-id").toList())
        verify(exactly = 0) { support.supports(any(), any(), any()) }
        verify(exactly = 0) { repository.prepare(any(), any()) }
        coVerify(exactly = 0) { repository.list(any()) }
    }

    @Test fun unverifiedReferenceMakesNoUploadAndDoesNotChangeProtocol() = runBlocking {
        val enabled = service.copy(isFileUploadEnabled = true)
        every { support.supports(enabled, "model", "application/pdf") } returns false
        assertEquals(listOf(CloudFilePreparation.Failed(CloudFileFailure.UnverifiedChatReference)),
            useCase(enabled, "model", attachment).toList())
        verify(exactly = 0) { repository.prepare(any(), any()) }
        assertEquals(ApiProtocol.OpenaiChatcompletions, enabled.apiProtocol)
    }

    @Test fun confirmedCapabilityUsesTheExactOriginalServiceAndAttachment() = runBlocking {
        val enabled = service.copy(isFileUploadEnabled = true)
        every { support.supports(enabled, "model", "application/pdf") } returns true
        every { repository.prepare(enabled, attachment) } returns flowOf(CloudFilePreparation.Uploading)
        assertEquals(listOf(CloudFilePreparation.Uploading), useCase(enabled, "model", attachment).toList())
        verify(exactly = 1) { repository.prepare(enabled, attachment) }
    }

    @Test fun unavailableCloudIdDoesNotStartReuploadOrTrustCallerMetadata() = runBlocking {
        val enabled = service.copy(isFileUploadEnabled = true)
        coEvery { repository.list(enabled) } returns emptyList()
        assertEquals(listOf(CloudFilePreparation.Failed(CloudFileFailure.LocalFileUnavailable)),
            useCase.existing(enabled, "model", "foreign-id").toList())
        verify(exactly = 0) { repository.prepareExisting(any(), any()) }
    }

    @Test fun cloudSelectionRechecksTheCurrentModelsReferenceCapability() = runBlocking {
        val enabled = service.copy(isFileUploadEnabled = true)
        val record = CloudFile("file", "scope", "a.pdf", "application/pdf", 10,
            "hash", 1000, 2000, null, null)
        coEvery { repository.list(enabled) } returns listOf(record)
        every { support.supports(enabled, "new-model", "application/pdf") } returns false
        assertEquals(listOf(CloudFilePreparation.Failed(CloudFileFailure.UnverifiedChatReference)),
            useCase.existing(enabled, "new-model", "file").toList())
        verify(exactly = 0) { repository.prepareExisting(any(), any()) }
    }
}
