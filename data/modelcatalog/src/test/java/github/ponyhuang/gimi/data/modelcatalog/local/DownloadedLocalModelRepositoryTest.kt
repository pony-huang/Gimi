package github.ponyhuang.gimi.data.modelcatalog.local

import android.content.SharedPreferences
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import io.mockk.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadedLocalModelRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val content = "fixture weights"
    private var enabled = emptySet<String>()
    private var staged = emptySet<String>()
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()
    init {
        every { preferences.getStringSet("enabled", any()) } answers { enabled }
        every { preferences.edit() } returns editor
        every { editor.putStringSet("enabled", any()) } answers { staged = secondArg<Set<String>>().toSet(); editor }
        every { editor.commit() } answers { enabled = staged; true }
    }

    @Test fun downloadsTwoVersionsAndRestoresIndependentEnableSelections() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(content)) }
            val repo = repository(server)
            repo.awaitReady()
            for (id in listOf("cpu", "gpu")) {
                repo.download(id)
                awaitReadyModel(repo, id)
                assertNull(repo.resolve(id))
                repo.setEnabled(id, true)
            }
            assertNotNull(repo.resolve("cpu"))
            assertNotNull(repo.resolve("gpu"))
            val restored = repository(server)
            restored.awaitReady()
            assertEquals(setOf("cpu", "gpu"), restored.state.value.models.filter { it.enabled }.map { it.variant.id }.toSet())
            restored.setEnabled("cpu", false)
            assertNull(restored.resolve("cpu"))
            assertNotNull(restored.resolve("gpu"))
            assertTrue(File(folder.root, "cpu.litertlm").isFile)
        }
    }

    @Test fun partialAndUnverifiedFilesCannotBeEnabledAndUnknownIdsCannotEscapeDirectory() = runBlocking {
        MockWebServer().use { server ->
            enabled = setOf("cpu", "gpu")
            File(folder.root, "cpu.litertlm.part").writeText("partial")
            File(folder.root, "gpu.litertlm").writeText(content)
            val repo = repository(server)
            repo.awaitReady()
            assertFalse(File(folder.root, "cpu.litertlm.part").exists())
            assertFalse(File(folder.root, "gpu.litertlm").exists())
            assertTrue(enabled.isEmpty())
            for (id in listOf("cpu", "gpu", "../escape")) {
                try { repo.setEnabled(id, true); fail("Unverified model must be rejected") } catch (_: IllegalStateException) { }
            }
            assertTrue(repo.state.value.models.none { it.enabled })
        }
    }

    @Test fun removalDeletesOnlyRequestedFileAndClearsPersistedSelection() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(content)) }
            val repo = repository(server)
            repo.awaitReady()
            for (id in listOf("cpu", "gpu")) { repo.download(id); awaitReadyModel(repo, id); repo.setEnabled(id, true) }
            for (id in listOf("cpu", "gpu")) {
                File(folder.root, "$id.litertlm.cache").mkdirs()
                File(folder.root, "$id.litertlm.cache/weights").writeText("cached weights")
            }
            repo.remove("cpu")
            assertFalse(File(folder.root, "cpu.litertlm").exists())
            assertFalse(File(folder.root, "cpu.verified").exists())
            assertFalse(File(folder.root, "cpu.litertlm.cache").exists())
            assertTrue(File(folder.root, "gpu.litertlm.cache/weights").isFile)
            assertNull(repo.resolve("cpu"))
            assertNotNull(repo.resolve("gpu"))
            assertEquals(setOf("gpu"), enabled)
            val restored = repository(server)
            restored.awaitReady()
            assertNull(restored.resolve("cpu"))
            assertNotNull(restored.resolve("gpu"))
        }
    }

    @Test fun failedDownloadMapsReasonAndCannotBeResolved() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            val repo = repository(server)
            repo.awaitReady(); repo.download("cpu")
            val failed = withTimeout(5_000) { repo.state.first { it.models.first().status == LocalModelDownloadStatus.Failed }.models.first() }
            assertEquals(LocalModelFailure.AccessDenied, failed.failure)
            assertFalse(failed.enabled)
            assertNull(repo.resolve("cpu"))
            assertFalse(File(folder.root, "cpu.litertlm").exists())
        }
    }

    private suspend fun awaitReadyModel(repo: DownloadedLocalModelRepository, id: String) = withTimeout(5_000) {
        repo.state.first { state -> state.models.any { it.variant.id == id && it.status == LocalModelDownloadStatus.Ready } }
    }

    private fun repository(server: MockWebServer) = DownloadedLocalModelRepository(
        folder.root, preferences, listOf("cpu", "gpu").map { id ->
            LocalModelDownloadSpec(LocalModelVariant(id, "gemma4", id, LocalModelBackend.CPU, content.length.toLong()),
                server.url("/$id").toString(), MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) })
        }, LocalModelFileDownloader(OkHttpClient()),
    )
}
