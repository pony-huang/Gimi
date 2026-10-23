package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 可选性由当前模型和真实来源决定，不能把上传记录直接当作可发送文件。 */
class LoadCloudFileChoicesUseCaseTest {
    private val repository = mockk<CloudFileRepository>()
    private val support = mockk<CloudFileChatSupport>()
    private val load = LoadCloudFileChoicesUseCase(repository, support)
    private val service = LLMModelSetting("deepseek", "DeepSeek", true, apiKey = "fake", apiBaseUrl = "https://example.test",
        apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = "", groups = emptyList(), isFileUploadEnabled = true)
    private val file = CloudFile("file", "scope", "report.pdf", "application/pdf", 10, "hash", 1, 2000, null, null)

    @Test fun disabledServiceDoesNotReadTheLibrary() = runBlocking {
        assertTrue(load(service.copy(isFileUploadEnabled = false), "model", 1000).isEmpty())
        coVerify(exactly = 0) { repository.list(any()) }
    }

    @Test fun otherProtocolsKeepTheStoredPreferenceButDoNotReadTheCloudLibrary() = runBlocking {
        for (protocol in listOf(ApiProtocol.Anthropic, ApiProtocol.Gemini)) {
            assertTrue(load(service.copy(apiProtocol = protocol), "model", 1000).isEmpty())
        }
        coVerify(exactly = 0) { repository.list(any()) }
    }

    @Test fun unverifiedReferencesCannotBeSelectedEvenWhenTheRemoteFileIsValid() = runBlocking {
        coEvery { repository.list(service) } returns listOf(file)
        every { support.supports(service, "model", file.mimeType) } returns false
        val choice = load(service, "model", 1000).single()
        assertEquals(CloudFileAvailability.ReferenceUnverified, choice.availability)
        assertFalse(choice.isSelectable)
        assertEquals(null, choice.toDraft())
        coVerify(exactly = 0) { repository.canReupload(any(), any()) }
    }

    @Test fun validCloudFilesNeedNoLocalSourceAndKeepTheirScopeInTheDraft() = runBlocking {
        coEvery { repository.list(service) } returns listOf(file)
        every { support.supports(service, "model", file.mimeType) } returns true
        val choice = load(service, "model", 1000).single()
        assertEquals(CloudFileAvailability.Ready, choice.availability)
        assertEquals(1000, choice.remainingMillis)
        assertEquals(file.scope, choice.toDraft()!!.cloudReference!!.scope)
        assertEquals(file.id, choice.toDraft()!!.cloudReference!!.fileId)
        coVerify(exactly = 0) { repository.canReupload(any(), any()) }
    }

    @Test fun expiredFilesRequireAnIntactLocalSourceToRemainSelectable() = runBlocking {
        coEvery { repository.list(service) } returns listOf(file, file.copy(id = "missing"))
        every { support.supports(service, "model", file.mimeType) } returns true
        coEvery { repository.canReupload(service, "file") } returns true
        coEvery { repository.canReupload(service, "missing") } returns false
        val choices = load(service, "model", 2000)
        assertEquals(listOf(CloudFileAvailability.ReuploadRequired, CloudFileAvailability.SourceUnavailable), choices.map { it.availability })
        assertTrue(choices.first().isSelectable)
        assertFalse(choices.last().isSelectable)
    }
}
