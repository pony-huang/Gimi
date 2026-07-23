package github.ponyhuang.asssistantai.data.skills

import app.cash.turbine.test
import github.ponyhuang.asssistantai.domain.skills.model.SkillImportFailure
import github.ponyhuang.asssistantai.domain.skills.model.SkillImportSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

class SkillRepositoryTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = createTempDirectory("skill-repository-").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun localDocumentImportUpdatesObservedSkills() = runTest {
        val archive = validArchive("local-skill", "Local skill")
        val reader = SkillArchiveReader(
            okHttpClient = OkHttpClient(),
            localDocumentOpener = { ByteArrayInputStream(archive) },
        )
        val repository = repository(reader, this)

        repository.observeInstalled().test {
            assertTrue(awaitItem().isEmpty())
            val prepared = repository.prepareImport(
                SkillImportSource.LocalDocument("content://skills/local.zip"),
            )
            repository.commitImport(prepared.id, allowReplace = false)
            assertEquals("local-skill", awaitItem().single().name)
            repository.remove("local-skill")
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rejectsNonHttpsUrlBeforeNetworkAccess() = runTest {
        val reader = SkillArchiveReader(
            okHttpClient = OkHttpClient(),
            localDocumentOpener = { null },
        )

        val failure = runCatching {
            reader.open(SkillImportSource.Url("http://example.com/skill.zip")).use { it.read() }
        }.exceptionOrNull() as SkillImportFailure

        assertEquals(SkillImportFailure.Reason.InvalidSource, failure.reason)
    }

    @Test
    fun opensHttpsArchiveResponse() = runTest {
        val archive = validArchive("remote-skill", "Remote")
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(archive.toResponseBody("application/zip".toMediaType()))
                    .build()
            }
            .build()
        val reader = SkillArchiveReader(client) { null }

        val bytes = reader.open(
            SkillImportSource.Url("https://example.com/remote.zip"),
        ).use { it.readBytes() }

        assertTrue(bytes.contentEquals(archive))
    }

    @Test
    fun rejectsMissingLocalDocument() = runTest {
        val reader = SkillArchiveReader(
            okHttpClient = OkHttpClient(),
            localDocumentOpener = { null },
        )

        val failure = runCatching {
            reader.open(SkillImportSource.LocalDocument("content://missing")).use { it.read() }
        }.exceptionOrNull() as SkillImportFailure

        assertEquals(SkillImportFailure.Reason.InvalidSource, failure.reason)
    }

    @Test
    fun importWorkRunsOnConfiguredWorkerDispatcher() = runTest {
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "skill-import-worker")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            var openerThread = ""
            val archive = validArchive("worker-skill", "Worker")
            val reader = SkillArchiveReader(
                okHttpClient = OkHttpClient(),
                localDocumentOpener = {
                    openerThread = Thread.currentThread().name
                    ByteArrayInputStream(archive)
                },
            )
            val repository = FileSkillRepository(
                store = SkillArchiveStore(File(root, "skills"), File(root, "staging")),
                archiveReader = reader,
                scope = this,
                workerDispatcher = dispatcher,
            )

            repository.prepareImport(SkillImportSource.LocalDocument("content://worker"))

            assertTrue(openerThread.contains("skill-import-worker"))
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private fun repository(reader: SkillArchiveReader, scope: TestScope) = FileSkillRepository(
        store = SkillArchiveStore(File(root, "skills"), File(root, "staging")),
        archiveReader = reader,
        scope = scope,
    )

    private fun validArchive(name: String, description: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("$name/SKILL.md"))
            zip.write(
                """
                    ---
                    name: $name
                    description: $description
                    ---

                    # Instructions
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }
}
