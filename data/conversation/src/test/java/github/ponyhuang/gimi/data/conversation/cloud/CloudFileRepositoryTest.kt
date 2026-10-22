package github.ponyhuang.gimi.data.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFilePreparation
import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 上传重试、跨连接隔离和到期规则的核心边界；不连接真实服务。 */
class CloudFileRepositoryTest {
    @get:Rule val directory = TemporaryFolder(File("../../temp/cloud-file-tests").apply { mkdirs() })
    private val service = cloudService()
    private val storage = MemoryCloudFileStorage()
    private var now = 1_700_000_000_000L
    private var uploadCount = 0
    private var upload: suspend () -> UploadedCloudFile = {
        uploadCount++
        UploadedCloudFile("file-$uploadCount", now, null)
    }
    private var downloadCount = 0
    private var download: suspend (File) -> Unit = { it.writeText("original") }
    private val remote = object : CloudFileRemoteGateway {
        override suspend fun upload(service: LLMModelSetting, attachment: DraftAttachment, source: File) = upload()
        override suspend fun download(service: LLMModelSetting, file: CloudFile, target: File) { downloadCount++; download(target) }
    }
    private val restoredDirectory get() = File(directory.root, "restored")
    private val repository by lazy { DefaultCloudFileRepository(storage, remote, restoredDirectory) { now } }

    @Test fun sameContentAndConcurrentRequestsUploadOnceWithinTheConnection() = runBlocking {
        val draft = draft("report.txt", "same content")
        val results = withTimeout(5_000) {
            List(3) { async { repository.prepare(service, draft).toList() } }.awaitAll()
        }
        assertEquals(1, uploadCount)
        assertTrue(results.all { it.last() is CloudFilePreparation.Ready })
        assertEquals(1, repository.list(service).size)
        assertEquals(1, results.count { CloudFilePreparation.Uploading in it })
    }

    @Test fun credentialsEndpointProtocolAndProviderIsolateRecords() = runBlocking {
        repository.prepare(service, draft("a.txt", "a")).toList()
        listOf(service.copy(apiKey = "other-key"), service.copy(apiBaseUrl = "https://example.test/v1"),
            service.copy(apiProtocol = ApiProtocol.Anthropic), service.copy(id = "other"))
            .forEach { assertTrue(repository.list(it).isEmpty()) }
        assertEquals(cloudFileScope(service), cloudFileScope(service.copy(apiBaseUrl = service.apiBaseUrl + "/")))
        assertFalse(storage.records.single().toString().contains(service.apiKey))
    }

    @Test fun changedRetentionAppliesOnlyToNewUploadsAndExpiredFilesReupload() = runBlocking {
        val attachment = draft("a.txt", "a")
        val first = ready(repository.prepare(service, attachment).toList())
        assertEquals(7 * 86_400_000L, first.expiresAtMillis - now)
        assertEquals(first, ready(repository.prepare(service.copy(fileUploadRetentionDays = 1), attachment).toList()))
        now = first.expiresAtMillis
        val next = ready(repository.prepareExisting(service.copy(fileUploadRetentionDays = 1), first.id).toList())
        assertEquals(2, uploadCount)
        assertEquals(86_400_000L, next.expiresAtMillis - now)
        assertEquals(null, next.remoteExpiresAtMillis)
    }

    @Test fun serverExpiryCanShortenButNeverExtendTheRequestedReuseWindow() = runBlocking {
        upload = { UploadedCloudFile("remote", now, now + 3_600_000L) }
        val first = ready(repository.prepare(service, draft("short.txt", "short")).toList())
        assertEquals(now + 3_600_000L, first.expiresAtMillis)
        upload = { UploadedCloudFile("long", now, now + 30 * 86_400_000L) }
        val second = ready(repository.prepare(service, draft("long.txt", "long")).toList())
        assertEquals(now + 7 * 86_400_000L, second.expiresAtMillis)
    }

    @Test fun validRemoteFileNeedsNoLocalSourceButExpiredFileDoes() = runBlocking {
        val attachment = draft("a.txt", "a")
        val first = ready(repository.prepare(service, attachment).toList())
        assertTrue(repository.canReupload(service, first.id))
        File(attachment.reference).delete()
        assertFalse(repository.canReupload(service, first.id))
        assertEquals(first, ready(repository.prepareExisting(service, first.id).toList()))
        now = first.expiresAtMillis
        assertEquals(CloudFilePreparation.Failed(CloudFileFailure.LocalFileUnavailable),
            repository.prepareExisting(service, first.id).toList().last())
        assertEquals(1, uploadCount)
    }

    @Test fun expiredFileCannotReuploadChangedLocalContentEvenWhenTheSizeMatches() = runBlocking {
        val attachment = draft("a.txt", "old")
        val first = ready(repository.prepare(service, attachment).toList())
        now = first.expiresAtMillis
        File(attachment.reference).writeText("new")
        assertFalse(repository.canReupload(service, first.id))
        assertEquals(CloudFilePreparation.Failed(CloudFileFailure.LocalFileUnavailable),
            repository.prepareExisting(service, first.id).toList().last())
        assertEquals(1, uploadCount)
    }

    @Test fun failureAndRetryLeaveOtherReadyFilesUntouched() = runBlocking {
        val first = ready(repository.prepare(service, draft("ready.txt", "a")).toList())
        val failed = draft("retry.txt", "b")
        upload = { throw CloudFileException(CloudFileFailure.UploadRejected) }
        assertEquals(CloudFilePreparation.Failed(CloudFileFailure.UploadRejected),
            repository.prepare(service, failed).toList().last())
        assertEquals(listOf(first), repository.list(service))
        upload = { UploadedCloudFile("retry", now, null) }
        ready(repository.prepare(service, failed).toList())
        assertEquals(2, repository.list(service).size)
        assertEquals(first, ready(repository.prepareExisting(service, first.id).toList()))
    }

    @Test fun cancellationDoesNotPublishReadyOrRetainTheConnectionLock() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        upload = { entered.complete(Unit); CompletableDeferred<UploadedCloudFile>().await() }
        val attachment = draft("a.txt", "a")
        val states = mutableListOf<CloudFilePreparation>()
        val task = launch { repository.prepare(service, attachment).toList(states) }
        withTimeout(5_000) { entered.await() }
        task.cancelAndJoin()
        assertEquals(listOf(CloudFilePreparation.Uploading), states)
        assertTrue(repository.list(service).isEmpty())
        upload = { UploadedCloudFile("retry", now, null) }
        withTimeout(5_000) { ready(repository.prepare(service, attachment).toList()) }
        assertEquals(1, repository.list(service).size)
    }

    @Test fun disabledServiceAndFailedStorageDoNotPublishReady() = runBlocking {
        val attachment = draft("a.txt", "a")
        assertEquals(listOf(CloudFilePreparation.Failed(CloudFileFailure.Disabled)),
            repository.prepare(service.copy(isFileUploadEnabled = false), attachment).toList())
        assertEquals(0, uploadCount)
        storage.failWrites = true
        assertEquals(CloudFilePreparation.Failed(CloudFileFailure.StorageUnavailable),
            repository.prepare(service, attachment).toList().last())
        assertTrue(storage.records.isEmpty())
    }

    @Test fun localOriginalRemainsUsableAfterRemoteExpirationWithoutDownloading() = runBlocking {
        val first = ready(repository.prepare(service, draft("image.png", "original")).toList())
        now = first.expiresAtMillis
        val resolved = repository.resolveLocal(service.copy(isFileUploadEnabled = false), CloudAttachmentReference(first.id, first.scope))
        assertEquals(first.localReference, resolved.localReference)
        assertEquals("original", File(requireNotNull(resolved.localReference)).readText())
        assertEquals(0, downloadCount)
    }

    @Test fun intactLocalPreviewDoesNotWaitForAnotherFilesUpload() = runBlocking {
        val first = ready(repository.prepare(service, draft("a.txt", "original")).toList())
        val entered = CompletableDeferred<Unit>()
        upload = { entered.complete(Unit); CompletableDeferred<UploadedCloudFile>().await() }
        val uploading = launch { repository.prepare(service, draft("b.txt", "another")).toList() }
        withTimeout(5_000) { entered.await() }
        try {
            val resolved = withTimeout(1_000) { repository.resolveLocal(service, CloudAttachmentReference(first.id, first.scope)) }
            assertEquals(first.localReference, resolved.localReference)
            assertEquals(0, downloadCount)
        } finally {
            uploading.cancelAndJoin()
        }
    }

    @Test fun missingLocalOriginalDownloadsOnceAndPersistsTheSharedPathForEveryConsumer() = runBlocking {
        val first = ready(repository.prepare(service, draft("image.png", "original")).toList())
        File(requireNotNull(first.localReference)).delete()
        val reference = CloudAttachmentReference(first.id, first.scope)
        val results = withTimeout(5_000) { List(3) { async { repository.resolveLocal(service, reference) } }.awaitAll() }
        assertEquals(1, downloadCount)
        assertEquals(1, results.map { it.localReference }.distinct().size)
        val restored = results.first()
        assertEquals("original", File(requireNotNull(restored.localReference)).readText())
        assertEquals(restored, repository.list(service).single())
        assertEquals(restored, repository.resolveLocal(service, reference))
        assertEquals(1, downloadCount)
        assertTrue(restoredDirectory.listFiles()!!.none { it.extension == "part" })
    }

    @Test fun failedIntegrityOrStorageNeverPublishesAPartialOriginal() = runBlocking {
        val first = ready(repository.prepare(service, draft("image.png", "original")).toList())
        File(requireNotNull(first.localReference)).delete()
        val reference = CloudAttachmentReference(first.id, first.scope)
        download = { it.writeText("modified") } // 大小相同也必须匹配上传时的 SHA-256。
        assertEquals(CloudFileFailure.InvalidResponse, localFailure { repository.resolveLocal(service, reference) }.reason)
        assertTrue(restoredDirectory.listFiles()!!.isEmpty())
        assertEquals(first, repository.list(service).single())
        download = { it.writeText("original") }
        storage.failWrites = true
        assertEquals(CloudFileFailure.StorageUnavailable, localFailure { repository.resolveLocal(service, reference) }.reason)
        assertTrue(restoredDirectory.listFiles()!!.isEmpty())
        assertEquals(first, repository.list(service).single())
    }

    @Test fun cancellationCleansPartialDownloadAndAllowsExplicitRetry() = runBlocking {
        val first = ready(repository.prepare(service, draft("a.txt", "original")).toList())
        File(requireNotNull(first.localReference)).delete()
        val reference = CloudAttachmentReference(first.id, first.scope)
        val entered = CompletableDeferred<Unit>()
        download = { it.writeText("orig"); entered.complete(Unit); CompletableDeferred<Unit>().await() }
        val task = launch { repository.resolveLocal(service, reference) }
        withTimeout(5_000) { entered.await() }
        task.cancelAndJoin()
        assertTrue(restoredDirectory.listFiles()!!.isEmpty())
        assertEquals(first, repository.list(service).single())
        download = { it.writeText("original") }
        assertTrue(File(requireNotNull(repository.resolveLocal(service, reference).localReference)).isFile)
    }

    @Test fun expiredRemoteOrChangedCredentialsNeverDownloadAnUnavailableSource() = runBlocking {
        val first = ready(repository.prepare(service, draft("a.txt", "original")).toList())
        val reference = CloudAttachmentReference(first.id, first.scope)
        assertEquals(CloudFileFailure.LocalFileUnavailable, localFailure {
            repository.resolveLocal(service.copy(apiKey = "changed"), reference)
        }.reason)
        File(requireNotNull(first.localReference)).delete()
        now = first.expiresAtMillis
        assertEquals(CloudFileFailure.LocalFileUnavailable, localFailure { repository.resolveLocal(service, reference) }.reason)
        assertEquals(0, downloadCount)
    }

    @Test fun dedupRebindsADeletedSourceToTheNewIntactLocalOriginal() = runBlocking {
        val first = ready(repository.prepare(service, draft("a.txt", "original")).toList())
        File(requireNotNull(first.localReference)).delete()
        val next = draft("b.txt", "original")
        val reused = ready(repository.prepare(service, next).toList())
        assertEquals(first.id, reused.id)
        assertEquals(next.reference, reused.localReference)
        assertEquals(1, uploadCount)
        assertTrue(repository.canReupload(service, reused.id))
    }

    private suspend fun localFailure(block: suspend () -> Unit): CloudFileException {
        try { block() } catch (error: CloudFileException) { return error }
        throw AssertionError("Expected local resolution failure")
    }

    private fun draft(name: String, text: String): DraftAttachment {
        val source = directory.newFile().apply { writeText(text) }
        return DraftAttachment(source.absolutePath, name, "text/plain", source.length(), AttachmentCategory.DOCUMENT)
    }

    private fun ready(states: List<CloudFilePreparation>): CloudFile = (states.last() as CloudFilePreparation.Ready).file
}

internal fun cloudService(id: String = "openai", endpoint: String = "https://api.openai.com/v1") = LLMModelSetting(
    id = id, name = id, isEnabled = true, isFileUploadEnabled = true, apiKey = "fake-test-key",
    apiBaseUrl = endpoint, apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = endpoint, groups = emptyList(),
)

internal class MemoryCloudFileStorage : CloudFileStorage {
    var records = emptyList<CloudFile>()
    var failWrites = false
    override fun read() = records
    override fun write(files: List<CloudFile>) {
        if (failWrites) throw CloudFileException(CloudFileFailure.StorageUnavailable)
        records = files
    }
}
