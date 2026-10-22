package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** 预览恢复的连接隔离；不能因为当前聊天切换模型而使用错误凭据下载历史文件。 */
class ResolveCloudFileLocalUseCaseTest {
    private val repository = mockk<CloudFileRepository>()
    private val resolve = ResolveCloudFileLocalUseCase(repository)
    private val service = LLMModelSetting("openai", "OpenAI", true, apiKey = "fake", apiBaseUrl = "https://example.test/v1",
        apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = "", groups = emptyList())
    private val file = CloudFile("file", "scope", "photo.png", "image/png", 10, "hash", 1, 2, null, "/shared/photo.png")

    @Test fun previewCanResolveWithUploadOffAndDoesNotRequireChatReferenceSupport() = runBlocking {
        coEvery { repository.list(service) } returns listOf(file)
        val reference = CloudAttachmentReference(file.id, file.scope)
        coEvery { repository.resolveLocal(service, reference) } returns file
        assertEquals(file.localReference, resolve(listOf(service), reference))
        coVerify(exactly = 1) { repository.resolveLocal(service, reference) }
        coVerify(exactly = 0) { repository.prepare(any(), any()) }
    }

    @Test fun matchingIdInAnotherScopeCannotTriggerDownload() = runBlocking {
        coEvery { repository.list(service) } returns listOf(file)
        try {
            resolve(listOf(service), CloudAttachmentReference(file.id, "old-credential-scope"))
            throw AssertionError("Expected scope rejection")
        } catch (error: CloudFileException) {
            assertEquals(CloudFileFailure.LocalFileUnavailable, error.reason)
        }
        coVerify(exactly = 0) { repository.resolveLocal(any(), any()) }
    }
}
