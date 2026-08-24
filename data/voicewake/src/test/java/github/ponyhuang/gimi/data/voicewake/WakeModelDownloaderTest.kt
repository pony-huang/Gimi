package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.core.network.HttpFileDownloader
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

class WakeModelDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: WakeModelDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = WakeModelDownloader(HttpFileDownloader(OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful download writes archive and reports progress`() {
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)).setHeader("Content-Length", payload.size))

        val archive = temporaryFolder.newFile("model.archive")
        val progresses = mutableListOf<Float>()
        downloadBlocking(
            url = server.url("/model.zip").toString(),
            sizeHintBytes = payload.size.toLong(),
            expectedSha256 = sha256(payload),
            archive = archive,
            onProgress = progresses::add,
        )

        assertTrue(archive.isFile)
        assertEquals(payload.size.toLong(), archive.length())
        assertEquals(0f, progresses.first())
        assertTrue(progresses.last() >= 1f - 0.01f)
        assertTrue(progresses.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `checksum mismatch deletes archive and throws ChecksumMismatch`() {
        val payload = ByteArray(1024) { 7 }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))

        val archive = temporaryFolder.newFile("model.archive")
        val error = assertThrows(WakeModelDownloadException::class.java) {
            downloadBlocking(
                url = server.url("/model.zip").toString(),
                sizeHintBytes = payload.size.toLong(),
                expectedSha256 = "0".repeat(64),
                archive = archive,
                onProgress = {},
            )
        }

        assertEquals(WakeModelDownloadException.Reason.ChecksumMismatch, error.reason)
        assertFalse(archive.exists())
    }

    @Test
    fun `http error throws Network and keeps no partial archive`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val archive = File(temporaryFolder.root, "model.archive")
        val error = assertThrows(WakeModelDownloadException::class.java) {
            downloadBlocking(
                url = server.url("/model.zip").toString(),
                sizeHintBytes = 1024,
                expectedSha256 = "0".repeat(64),
                archive = archive,
                onProgress = {},
            )
        }

        assertEquals(WakeModelDownloadException.Reason.Network, error.reason)
        assertFalse(archive.exists())
    }

    @Test
    fun `unreachable server throws Network`() {
        server.shutdown()
        val archive = File(temporaryFolder.root, "model.archive")
        val error = assertThrows(WakeModelDownloadException::class.java) {
            downloadBlocking(
                url = server.url("/model.zip").toString(),
                sizeHintBytes = 1024,
                expectedSha256 = "0".repeat(64),
                archive = archive,
                onProgress = {},
            )
        }

        assertEquals(WakeModelDownloadException.Reason.Network, error.reason)
        assertFalse(archive.exists())
    }

    @Test
    fun `cancelling download stops network transfer and deletes partial archive`() = runBlocking {
        val slowServer = MockWebServer()
        val payload = ByteArray(512 * 1024) { (it % 251).toByte() }
        slowServer.enqueue(
            MockResponse()
                .setBody(Buffer().write(payload))
                .throttleBody(1024, 100, TimeUnit.MILLISECONDS),
        )
        slowServer.start()
        val archive = File(temporaryFolder.root, "cancelled.archive")
        val firstChunk = CompletableDeferred<Unit>()
        val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = downloadScope.launch {
            downloader.download(
                url = slowServer.url("/slow-model.zip").toString(),
                sizeHintBytes = payload.size.toLong(),
                expectedSha256 = sha256(payload),
                archive = archive,
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
        assertFalse(archive.exists())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun downloadBlocking(
        url: String,
        sizeHintBytes: Long,
        expectedSha256: String,
        archive: File,
        onProgress: (Float) -> Unit,
    ) = runBlocking {
        downloader.download(url, sizeHintBytes, expectedSha256, archive, onProgress)
    }
}
