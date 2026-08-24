package github.ponyhuang.gimi.core.network

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HttpFileDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: HttpFileDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = HttpFileDownloader(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful download writes file verifies checksum and reports early progress`() = runBlocking {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(payload))
                .setHeader("Content-Length", payload.size),
        )
        val destination = File(temporaryFolder.root, "download.bin")
        val progresses = mutableListOf<Float>()

        downloader.download(
            url = server.url("/download.bin").toString(),
            sizeHintBytes = payload.size.toLong(),
            expectedSha256 = sha256(payload),
            destination = destination,
            onProgress = progresses::add,
        )

        assertEquals(payload.size.toLong(), destination.length())
        assertEquals(0f, progresses.first())
        assertTrue(progresses.first { it > 0f } < 0.01f)
        assertTrue(progresses.last() >= 1f - 0.01f)
    }

    @Test
    fun `cancelling download stops transfer and deletes partial file`() = runBlocking {
        val slowServer = MockWebServer()
        val payload = ByteArray(512 * 1024) { (it % 251).toByte() }
        slowServer.enqueue(
            MockResponse()
                .setBody(Buffer().write(payload))
                .throttleBody(1024, 100, TimeUnit.MILLISECONDS),
        )
        slowServer.start()
        val destination = File(temporaryFolder.root, "cancelled.bin")
        val firstChunk = CompletableDeferred<Unit>()
        val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = downloadScope.launch {
            downloader.download(
                url = slowServer.url("/slow.bin").toString(),
                sizeHintBytes = payload.size.toLong(),
                expectedSha256 = null,
                destination = destination,
                onProgress = { progress ->
                    if (progress > 0f) firstChunk.complete(Unit)
                },
            )
        }

        val stoppedPromptly = try {
            withTimeout(5_000) { firstChunk.await() }
            job.cancel()
            withTimeoutOrNull(750) {
                job.join()
                true
            } ?: false
        } finally {
            slowServer.shutdown()
            downloadScope.cancel()
            job.join()
        }

        assertTrue("Cancellation should stop the active HTTP transfer", stoppedPromptly)
        assertFalse(destination.exists())
    }

    @Test
    fun `http error throws Network and deletes destination`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val destination = File(temporaryFolder.root, "failed.bin")

        val error = assertThrows(HttpFileDownloadException::class.java) {
            downloadBlocking(destination, expectedSha256 = null)
        }

        assertEquals(HttpFileDownloadException.Reason.Network, error.reason)
        assertFalse(destination.exists())
    }

    @Test
    fun `checksum mismatch throws ChecksumMismatch and deletes destination`() {
        val payload = ByteArray(1024) { 7 }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        val destination = File(temporaryFolder.root, "mismatch.bin")

        val error = assertThrows(HttpFileDownloadException::class.java) {
            downloadBlocking(destination, expectedSha256 = "0".repeat(64))
        }

        assertEquals(HttpFileDownloadException.Reason.ChecksumMismatch, error.reason)
        assertFalse(destination.exists())
    }

    private fun downloadBlocking(destination: File, expectedSha256: String?) = runBlocking {
        downloader.download(
            url = server.url("/download.bin").toString(),
            sizeHintBytes = 1024,
            expectedSha256 = expectedSha256,
            destination = destination,
            onProgress = {},
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
