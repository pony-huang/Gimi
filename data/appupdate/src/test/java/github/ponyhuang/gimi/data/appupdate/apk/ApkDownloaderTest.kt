package github.ponyhuang.gimi.data.appupdate.apk

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

class ApkDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: ApkDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = ApkDownloader(HttpFileDownloader(OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `download reports the first transferred bytes before one percent`() = runBlocking {
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(payload))
                .setHeader("Content-Length", payload.size),
        )
        val progresses = mutableListOf<Float>()

        downloader.download(
            url = server.url("/app.apk").toString(),
            sizeHintBytes = payload.size.toLong(),
            expectedSha256 = null,
            dest = File(temporaryFolder.root, "app.apk"),
            onProgress = progresses::add,
        )

        val firstTransferredProgress = progresses.first { it > 0f }
        assertTrue(
            "The status notification should not wait for a full percent before its first refresh",
            firstTransferredProgress < 0.01f,
        )
    }

    @Test
    fun `cancelling download stops network transfer and deletes partial apk`() = runBlocking {
        val slowServer = MockWebServer()
        val payload = ByteArray(512 * 1024) { (it % 251).toByte() }
        slowServer.enqueue(
            MockResponse()
                .setBody(Buffer().write(payload))
                .throttleBody(1024, 100, TimeUnit.MILLISECONDS),
        )
        slowServer.start()
        val destination = File(temporaryFolder.root, "cancelled.apk")
        val firstChunk = CompletableDeferred<Unit>()
        val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = downloadScope.launch {
            downloader.download(
                url = slowServer.url("/slow-app.apk").toString(),
                sizeHintBytes = payload.size.toLong(),
                expectedSha256 = null,
                dest = destination,
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

        assertTrue("Cancellation should stop the active APK transfer", stoppedPromptly)
        assertTrue("Cancellation should delete the partial APK", !destination.exists())
    }

    @Test
    fun `http error throws Network and keeps no partial apk`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val destination = File(temporaryFolder.root, "failed.apk")

        val error = assertThrows(ApkDownloadException::class.java) {
            runBlocking {
                downloader.download(
                    url = server.url("/failed.apk").toString(),
                    sizeHintBytes = 1024,
                    expectedSha256 = null,
                    dest = destination,
                    onProgress = {},
                )
            }
        }

        assertEquals(ApkDownloadException.Reason.Network, error.reason)
        assertFalse(destination.exists())
    }

    @Test
    fun `checksum mismatch deletes apk and throws ChecksumMismatch`() {
        val payload = ByteArray(1024) { 7 }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        val destination = File(temporaryFolder.root, "mismatch.apk")

        val error = assertThrows(ApkDownloadException::class.java) {
            runBlocking {
                downloader.download(
                    url = server.url("/mismatch.apk").toString(),
                    sizeHintBytes = payload.size.toLong(),
                    expectedSha256 = sha256(ByteArray(1) { 1 }),
                    dest = destination,
                    onProgress = {},
                )
            }
        }

        assertEquals(ApkDownloadException.Reason.ChecksumMismatch, error.reason)
        assertFalse(destination.exists())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
