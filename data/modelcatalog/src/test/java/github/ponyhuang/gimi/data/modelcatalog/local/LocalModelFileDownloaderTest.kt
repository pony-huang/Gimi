package github.ponyhuang.gimi.data.modelcatalog.local

import github.ponyhuang.gimi.domain.modelcatalog.model.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalModelFileDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = "test model weights".toByteArray()

    @Test fun publishesOnlyVerifiedCompleteFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val spec = spec(server)
            val target = File(folder.root, "model.litertlm")
            val progress = mutableListOf<Float>()
            LocalModelFileDownloader(OkHttpClient()).download(spec, target, progress::add) {}
            assertArrayEquals(bytes, target.readBytes())
            assertEquals(1f, progress.last())
            assertFalse(File(folder.root, "model.litertlm.part").exists())
            assertEquals("/weights", server.takeRequest().path)
        }
    }

    @Test fun rejectsHttpErrorHashMismatchAndTruncatedFiles() = runBlocking {
        for (response in listOf(
            MockResponse().setResponseCode(403).setBody("denied"),
            MockResponse().setBody("wrong model bytes!"),
            MockResponse().setChunkedBody("short", 2),
        )) {
            MockWebServer().use { server ->
                server.enqueue(response)
                val target = File(folder.root, "model.litertlm")
                try {
                    LocalModelFileDownloader(OkHttpClient()).download(spec(server), target, {}) {}
                    fail("Invalid model must not be published")
                } catch (_: LocalModelDownloadException) { }
                assertFalse(target.exists())
                assertFalse(File(folder.root, "model.litertlm.part").exists())
            }
        }
    }

    @Test fun cancellationClosesTransferAndRemovesPartialFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)).throttleBody(1, 1, TimeUnit.SECONDS))
            val target = File(folder.root, "model.litertlm")
            val job = launch(Dispatchers.IO) { LocalModelFileDownloader(OkHttpClient()).download(spec(server), target, {}) {} }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
            withTimeout(5_000) { job.cancelAndJoin() }
            assertFalse(target.exists())
            assertFalse(File(folder.root, "model.litertlm.part").exists())
        }
    }

    @Test fun insufficientStorageDoesNotStartHttpRequest() = runBlocking {
        MockWebServer().use { server ->
            val target = File(folder.root, "model.litertlm")
            val spec = spec(server).let { it.copy(variant = it.variant.copy(bytes = Long.MAX_VALUE - 128L * 1024 * 1024)) }
            try { LocalModelFileDownloader(OkHttpClient()).download(spec, target, {}) {}; fail("Must reject unavailable storage") }
            catch (error: LocalModelDownloadException) { assertEquals(LocalModelFailure.Storage, error.reason) }
            assertEquals(0, server.requestCount)
            assertFalse(target.exists())
        }
    }

    private fun spec(server: MockWebServer) = LocalModelDownloadSpec(
        LocalModelVariant("test", "gemma4", "Test", LocalModelBackend.CPU, bytes.size.toLong()),
        server.url("/weights").toString(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
    )
}
