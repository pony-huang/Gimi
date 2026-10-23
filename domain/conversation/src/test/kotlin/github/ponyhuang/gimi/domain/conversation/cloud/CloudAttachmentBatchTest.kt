package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 一次发送的等待、逐文件重试和取消边界；不调用真实模型或厂商 API。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudAttachmentBatchTest {
    private val source = mockk<CloudFileLocalSource>()
    private val repository = mockk<CloudFileRepository>()
    private val support = mockk<CloudFileChatSupport>()
    private val factory = CloudAttachmentBatchFactory(source, PrepareCloudFileUseCase(repository, support), support)
    private val service = LLMModelSetting("openai", "OpenAI", true, apiKey = "fake", apiBaseUrl = "https://example.test/v1",
        apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = "", groups = emptyList(), isFileUploadEnabled = true)
    private val drafts = listOf(draft("one"), draft("two"))

    private fun allow() {
        every { support.supports(any(), any(), any()) } returns true
        coEvery { source.archive(any(), any()) } answers {
            val file = secondArg<DraftAttachment>()
            FileAttachment(file.mimeType, file.displayName, file.sizeBytes, file.displayName, "/archive/${file.displayName}")
        }
    }

    @Test fun firstReadyFileDoesNotSendUntilEveryRequiredFileIsReadyAndOrderIsStable() = runTest {
        allow()
        val second = CompletableDeferred<Unit>()
        every { repository.prepare(service, any()) } answers {
            val draft = secondArg<DraftAttachment>()
            flow {
                emit(CloudFilePreparation.Uploading)
                if (draft.displayName == "two.pdf") second.await()
                emit(CloudFilePreparation.Ready(record(draft.displayName)))
            }
        }
        val batch = factory.create("session", service, "model", drafts)
        val task = async { batch.awaitReady() }
        runCurrent()
        assertTrue(batch.items.value[0].state is CloudAttachmentState.Ready)
        assertEquals(CloudAttachmentState.Uploading, batch.items.value[1].state)
        assertFalse(task.isCompleted)
        second.complete(Unit)
        assertEquals(listOf("one.pdf", "two.pdf"), task.await()!!.map { it.cloudReference!!.fileId })
    }

    @Test fun retryUploadsOnlyTheFailedFileAndReusesItsArchive() = runTest {
        allow()
        val counts = mutableMapOf<String, Int>()
        every { repository.prepare(service, any()) } answers {
            val name = secondArg<DraftAttachment>().displayName
            val count = counts.getOrDefault(name, 0) + 1
            counts[name] = count
            flowOf(if (name == "two.pdf" && count == 1) CloudFilePreparation.Failed(CloudFileFailure.UploadRejected)
                else CloudFilePreparation.Ready(record(name)))
        }
        val batch = factory.create("session", service, "model", drafts)
        val task = async { batch.awaitReady() }
        runCurrent()
        assertFalse(task.isCompleted)
        batch.retry(0) // 已就绪文件不重传。
        batch.retry(1)
        assertEquals(2, task.await()!!.size)
        assertEquals(mapOf("one.pdf" to 1, "two.pdf" to 2), counts)
        coVerify(exactly = 2) { source.archive(any(), any()) }
    }

    @Test fun singleCancellationKeepsTheWholeSendWaitingUntilThatFileIsRetried() = runTest {
        allow()
        var attempts = 0
        every { repository.prepare(service, any()) } answers {
            val name = secondArg<DraftAttachment>().displayName
            flow {
                emit(CloudFilePreparation.Uploading)
                if (name == "two.pdf" && attempts++ == 0) CompletableDeferred<Unit>().await()
                emit(CloudFilePreparation.Ready(record(name)))
            }
        }
        val batch = factory.create("session", service, "model", drafts)
        val task = async { batch.awaitReady() }
        runCurrent()
        batch.cancel(1)
        runCurrent()
        assertEquals(CloudAttachmentState.Cancelled, batch.items.value[1].state)
        assertFalse(task.isCompleted)
        batch.retry(1)
        assertEquals(2, task.await()!!.size)
    }

    @Test fun cancellationOfTheBatchCancelsPendingWorkAndNeverReturnsAPartialInput() = runTest {
        allow()
        every { repository.prepare(service, any()) } returns flow {
            emit(CloudFilePreparation.Uploading)
            CompletableDeferred<Unit>().await()
        }
        val batch = factory.create("session", service, "model", drafts)
        val task = async { batch.awaitReady() }
        runCurrent()
        task.cancelAndJoin()
        assertTrue(batch.items.value.all { it.state == CloudAttachmentState.Cancelled })
        assertTrue(task.isCancelled)
    }

    @Test fun unverifiedCapabilityAndDisabledPreferenceRejectBeforeAnySourceOrUploadSideEffect() = runTest {
        every { support.supports(any(), any(), any()) } returns false
        val batch = factory.create("session", service, "model", drafts)
        assertNull(batch.awaitReady())
        assertTrue(batch.items.value.all { it.state == CloudAttachmentState.Failed(CloudFileFailure.UnverifiedChatReference) })
        assertNull(factory.create("session", service.copy(isFileUploadEnabled = false), "model", drafts).awaitReady())
        coVerify(exactly = 0) { source.archive(any(), any()) }
    }

    @Test fun fileRequestLimitRejectsBeforeArchivingOrUploadingAnyFile() = runTest {
        allow()
        val oversized = drafts.map { it.copy(sizeBytes = CloudFileRequestLimits.MAX_BYTES) }
        val batch = factory.create("session", service, "model", oversized)
        assertNull(batch.awaitReady())
        assertTrue(batch.items.value.all { it.state == CloudAttachmentState.Failed(CloudFileFailure.FileTooLarge) })
        coVerify(exactly = 0) { source.archive(any(), any()) }
    }

    @Test fun existingCloudIdIsRevalidatedWithoutArchivingAndCannotCrossItsOriginalScope() = runTest {
        allow()
        val cloud = drafts.first().copy(reference = "", cloudReference = CloudAttachmentReference("id", "original-scope"))
        coEvery { repository.list(service) } returns listOf(record("id").copy(scope = "different-scope"))
        val batch = factory.create("session", service, "model", listOf(cloud))
        val task = async { batch.awaitReady() }
        runCurrent()
        assertEquals(CloudAttachmentState.Failed(CloudFileFailure.InvalidConfiguration), batch.items.value.single().state)
        assertFalse(task.isCompleted)
        coVerify(exactly = 0) { source.archive(any(), any()) }
        task.cancelAndJoin()
    }

    @Test fun localSourceFailureIsPerFileAndCanBeRetriedWithoutTouchingTheOtherFile() = runTest {
        allow()
        var attempts = 0
        coEvery { source.archive("session", drafts.first()) } coAnswers {
            if (attempts++ == 0) throw IOException("private path must not escape")
            FileAttachment("application/pdf", "one", 10, "one.pdf", "/archive/one.pdf")
        }
        every { repository.prepare(service, any()) } answers { flowOf(CloudFilePreparation.Ready(record(secondArg<DraftAttachment>().displayName))) }
        val batch = factory.create("session", service, "model", drafts)
        val task = async { batch.awaitReady() }
        runCurrent()
        assertEquals(CloudAttachmentState.Failed(CloudFileFailure.LocalFileUnavailable), batch.items.value.first().state)
        assertTrue(batch.items.value.last().state is CloudAttachmentState.Ready)
        batch.retry(0)
        assertEquals(2, task.await()!!.size)
    }

    @Test fun readyFileThatExpiresWhileAnotherUploadsIsPreparedAgainBeforeSending() = runTest {
        allow()
        var now = 1000L
        var firstAttempts = 0
        val second = CompletableDeferred<Unit>()
        every { repository.prepare(service, any()) } answers {
            val name = secondArg<DraftAttachment>().displayName
            flow {
                emit(CloudFilePreparation.Uploading)
                if (name == "two.pdf") second.await()
                val file = if (name == "one.pdf" && firstAttempts++ == 0)
                    record("expired-id").copy(expiresAtMillis = 2000) else record("fresh-$name")
                emit(CloudFilePreparation.Ready(file))
            }
        }
        val batch = CloudAttachmentBatch("session", service, "model", drafts, source,
            PrepareCloudFileUseCase(repository, support), support, nowMillis = { now })
        val task = async { batch.awaitReady() }
        runCurrent()
        now = 3000
        second.complete(Unit)
        assertEquals(listOf("fresh-one.pdf", "fresh-two.pdf"), task.await()!!.map { it.cloudReference!!.fileId })
        assertEquals(2, firstAttempts)
        coVerify(exactly = 2) { source.archive(any(), any()) }
    }

    private fun draft(name: String) = DraftAttachment("/draft/$name", "$name.pdf", "application/pdf", 10, AttachmentCategory.DOCUMENT)
    private fun record(id: String) = CloudFile(id, "scope", id, "application/pdf", 10, "hash", 1,
        Long.MAX_VALUE, null, "/archive/$id")
}
