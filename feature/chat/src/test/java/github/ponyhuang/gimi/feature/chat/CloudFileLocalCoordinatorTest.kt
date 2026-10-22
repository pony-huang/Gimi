package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import github.ponyhuang.gimi.domain.conversation.cloud.ResolveCloudFileLocalUseCase
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 原文件恢复生命周期的回归边界，不运行 UI；防止旧凭据的迟到下载回写当前界面。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudFileLocalCoordinatorTest {
    private val resolve = mockk<ResolveCloudFileLocalUseCase>()
    private val reference = CloudAttachmentReference("file", "scope")
    private var services = listOf(LLMModelSetting("openai", "OpenAI", true, apiKey = "fake", apiBaseUrl = "https://example.test/v1",
        apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = "", groups = emptyList()))
    private var paths = emptyMap<CloudAttachmentReference, String?>()

    @Test fun repeatedConsumersReuseOneResolutionAndResetAllowsRecoveryAfterLocalCleanup() = runTest {
        coEvery { resolve(any(), reference) } returns "/shared/original.png"
        val coordinator = CloudFileLocalCoordinator(this, resolve, { services }, { paths = it })
        repeat(3) { coordinator.request(setOf(reference)) }
        advanceUntilIdle()
        assertEquals("/shared/original.png", paths[reference])
        coVerify(exactly = 1) { resolve(any(), reference) }
        coordinator.reset()
        coordinator.request(setOf(reference))
        advanceUntilIdle()
        coVerify(exactly = 2) { resolve(any(), reference) }
    }

    @Test fun unvalidatedOrUnavailableCloudImageNeverFallsBackToAnOldLocalPath() {
        val image = github.ponyhuang.gimi.domain.conversation.model.FileAttachment("image/png", "image", 12,
            "photo.png", payloadReference = "/old/credential.png", cloudReference = reference)
        val pending = image.withCloudLocal(emptyMap())
        assertTrue(pending.isMissing)
        assertEquals(null, pending.payloadReference)
        val unavailable = image.withCloudLocal(mapOf(reference to null))
        assertTrue(unavailable.isMissing)
        assertEquals(null, unavailable.payloadReference)
        val recovered = image.withCloudLocal(mapOf(reference to "/shared/original.png"))
        assertEquals("/shared/original.png", recovered.payloadReference)
        assertEquals(reference, recovered.cloudReference)
        assertEquals("/old/credential.png", image.payloadReference)
    }

    @Test fun credentialChangeDiscardsLateResultsAndUnavailableFileDoesNotLoopDownloads() = runTest {
        val old = CompletableDeferred<String>()
        var calls = 0
        coEvery { resolve(any(), reference) } coAnswers {
            if (calls++ == 0) withContext(NonCancellable) { old.await() }
            else throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
        }
        val coordinator = CloudFileLocalCoordinator(this, resolve, { services }, { paths = it })
        coordinator.request(setOf(reference))
        runCurrent()
        services = services.map { it.copy(apiKey = "changed") }
        coordinator.request(setOf(reference))
        runCurrent()
        old.complete("/old/credential.png")
        advanceUntilIdle()
        assertTrue(reference in paths)
        assertEquals(null, paths[reference])
        coordinator.request(setOf(reference))
        advanceUntilIdle()
        coVerify(exactly = 2) { resolve(any(), reference) }
    }
}
