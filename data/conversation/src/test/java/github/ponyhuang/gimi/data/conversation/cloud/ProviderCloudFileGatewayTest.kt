package github.ponyhuang.gimi.data.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 厂商请求映射与取消边界，所有 HTTP 均发送到本地 MockWebServer。 */
class ProviderCloudFileGatewayTest {
    @get:Rule val directory = TemporaryFolder(File("../../temp/cloud-file-tests").apply { mkdirs() })
    private lateinit var server: MockWebServer
    private val gateway = ProviderCloudFileGateway()
    private lateinit var source: File
    private lateinit var attachment: DraftAttachment

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        source = directory.newFile().apply { writeText("sample content") }
        attachment = DraftAttachment(source.absolutePath, "report.txt", "text/plain", source.length(), AttachmentCategory.DOCUMENT)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun openAiSdkSendsOriginalNameUserDataAndExpiration() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"id":"file-openai","object":"file","bytes":14,"created_at":1700000000,
                "expires_at":1700604800,"filename":"report.txt","purpose":"user_data","status":"processed"}"""))
        val result = gateway.upload(cloudService(endpoint = server.url("/v1").toString()), attachment, source)
        val request = server.takeRequest()
        assertEquals("/v1/files", request.path)
        assertEquals("POST", request.method)
        assertEquals("Bearer fake-test-key", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("filename=\"report.txt\""))
        assertTrue(body.contains("user_data"))
        assertTrue(body.contains("created_at"))
        assertTrue(body.contains("604800"))
        assertTrue(body.contains("sample content"))
        assertEquals("file-openai", result.id)
        assertEquals(1_700_604_800_000L, result.remoteExpiresAtMillis)
    }

    @Test fun deepSeekUsesOfficialFilesDialectWithoutChangingChatProtocol() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"file-deepseek","type":"file","size_bytes":14,
            "created_at":"2026-10-22T00:00:00Z","filename":"report.txt","mime_type":"text/plain"}"""))
        val service = cloudService("deepseek", server.url("/v1").toString()).copy(fileUploadRetentionDays = 30)
        val result = gateway.upload(service, attachment, source)
        val request = server.takeRequest()
        assertEquals("/anthropic/v1/files", request.path)
        assertEquals("fake-test-key", request.getHeader("x-api-key"))
        assertEquals(null, request.getHeader("Authorization"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        assertEquals("files-api-2025-04-14", request.getHeader("anthropic-beta"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("expires_after[anchor]"))
        assertTrue(body.contains("2592000"))
        assertFalse(body.contains("name=\"purpose\""))
        assertEquals("file-deepseek", result.id)
        assertEquals(null, result.remoteExpiresAtMillis)
        assertEquals("OpenaiChatcompletions", service.apiProtocol.name)
    }

    @Test fun retryableStatusDoesNotAutomaticallyRepeatEitherUpload() = runBlocking {
        for (provider in listOf("openai", "deepseek")) {
            server.enqueue(MockResponse().setResponseCode(429)
                .setHeader("Content-Type", "application/json").setHeader("Retry-After", "0")
                .setBody("""{"error":{"message":"fake provider secret must not escape"}}"""))
            val failure = failure {
                gateway.upload(cloudService(provider, server.url("/v1").toString()), attachment, source)
            }
            assertEquals(CloudFileFailure.UploadRejected, failure.reason)
            assertFalse(failure.toString().contains("fake provider secret"))
            server.takeRequest()
            assertEquals(null, server.takeRequest(200, TimeUnit.MILLISECONDS))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun malformedDeepSeekMetadataFailsWithoutLeakingTheResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"sensitive-response","created_at":"not-a-date"}"""))
        val error = failure {
            gateway.upload(cloudService("deepseek", server.url("/v1").toString()), attachment, source)
        }
        assertEquals(CloudFileFailure.InvalidResponse, error.reason)
        assertFalse(error.toString().contains("sensitive-response"))
    }

    @Test fun unsupportedProviderMakesNoRequest() = runBlocking {
        val error = failure {
            gateway.upload(cloudService("claude", server.url("/v1").toString()), attachment, source)
        }
        assertEquals(CloudFileFailure.UnsupportedProvider, error.reason)
        assertEquals(0, server.requestCount)
    }

    @Test fun cancellationStopsWaitingForBothProviders() = runBlocking {
        for (provider in listOf("openai", "deepseek")) {
            server.enqueue(MockResponse().setHeadersDelay(30, TimeUnit.SECONDS).setBody("{}"))
            val task = async(Dispatchers.IO) {
                gateway.upload(cloudService(provider, server.url("/v1").toString()), attachment, source)
            }
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
            withTimeout(5_000) { task.cancelAndJoin() }
            assertTrue(task.isCancelled)
        }
    }

    private fun downloadable() = CloudFile("file-original", "scope", "report.txt", "text/plain", 14,
        "hash", 1, Long.MAX_VALUE, null, null)

    @Test fun openAiDownloadsOriginalBytesUsingAuthenticatedContentEndpoint() = runBlocking {
        server.enqueue(MockResponse().setBody("sample content"))
        val target = directory.newFile()
        gateway.download(cloudService(endpoint = server.url("/v1").toString()), downloadable(), target)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/files/file-original/content", request.path)
        assertEquals("Bearer fake-test-key", request.getHeader("Authorization"))
        assertEquals("sample content", target.readText())
    }

    @Test fun unsupportedDownloadDoesNotGuessDeepSeekEndpoint() = runBlocking {
        val error = failure { gateway.download(cloudService("deepseek", server.url("/v1").toString()), downloadable(), directory.newFile()) }
        assertEquals(CloudFileFailure.LocalFileUnavailable, error.reason)
        assertEquals(0, server.requestCount)
    }

    @Test fun downloadRejectsOversizedContentAndDoesNotRetryAnErrorResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("too much original content"))
        assertEquals(CloudFileFailure.InvalidResponse, failure {
            gateway.download(cloudService(endpoint = server.url("/v1").toString()), downloadable(), directory.newFile())
        }.reason)
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Content-Type", "application/json")
            .setBody("""{"error":{"message":"provider-private-error"}}"""))
        val error = failure { gateway.download(cloudService(endpoint = server.url("/v1").toString()), downloadable(), directory.newFile()) }
        assertEquals(CloudFileFailure.LocalFileUnavailable, error.reason)
        assertFalse(error.toString().contains("provider-private-error"))
        server.takeRequest()
        assertEquals(null, server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test fun cancellingDuringContentReadClosesTheDownload() = runBlocking {
        server.enqueue(MockResponse().setBody("sample content").throttleBody(1, 30, TimeUnit.SECONDS))
        val task = async(Dispatchers.IO) {
            gateway.download(cloudService(endpoint = server.url("/v1").toString()), downloadable(), directory.newFile())
        }
        assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
        withTimeout(5_000) { task.cancelAndJoin() }
        assertTrue(task.isCancelled)
    }

    private suspend fun failure(block: suspend () -> Unit): CloudFileException {
        try { block() } catch (error: CloudFileException) { return error }
        throw AssertionError("Expected CloudFileException")
    }
}
