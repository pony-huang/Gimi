package github.ponyhuang.gimi.data.skills

import app.cash.turbine.test
import com.google.adk.kt.skills.NewFileSystemSource
import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.domain.skills.model.SkillImportFailure
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import github.ponyhuang.gimi.domain.skills.usecase.InstallSkillArchiveUseCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
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
    fun binaryArchiveUsesExistingInstallerAndIsImmediatelyDiscoverable() = runTest {
        val repository = repository(SkillArchiveReader(OkHttpClient()) { error("No URI expected") }, this)
        val liveSource = NewFileSystemSource(File(root, "skills").absolutePath)
        assertTrue(liveSource.listFrontmatters().getOrThrow().isEmpty())
        val source = SkillImportSource.ArchiveBytes(validArchive("sandbox-skill", "Sandbox skill"))
        val installed = InstallSkillArchiveUseCase(repository)(source)

        assertEquals("sandbox-skill", installed.name)
        assertEquals("sandbox-skill", liveSource.listFrontmatters().getOrThrow().single().name)
        assertTrue(liveSource.loadInstructions("sandbox-skill").getOrThrow().contains("# Instructions"))
        assertTrue(liveSource.loadResource("sandbox-skill", "assets/data.bin").getOrThrow()
            .contentEquals(byteArrayOf(0, -1, 7)))
        assertTrue(File(root, "skills/sandbox-skill/SKILL.md").readText().contains("# Instructions"))
        assertTrue(File(root, "staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun binaryArchiveStillRejectsInvalidStructureAndCleansStaging() = runTest {
        val repository = repository(SkillArchiveReader(OkHttpClient()) { null }, this)
        val failure = runCatching {
            repository.prepareImport(SkillImportSource.ArchiveBytes("not a zip".toByteArray()))
        }.exceptionOrNull() as SkillImportFailure
        assertEquals(SkillImportFailure.Reason.InvalidStructure, failure.reason)
        assertTrue(File(root, "staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun binaryInputRejectsOversizedArchiveBeforeExtraction() = runTest {
        val reader = SkillArchiveReader(OkHttpClient()) { null }
        val failure = runCatching {
            reader.open(SkillImportSource.ArchiveBytes(ByteArray(20 * 1024 * 1024 + 1)))
        }.exceptionOrNull() as SkillImportFailure
        assertEquals(SkillImportFailure.Reason.ArchiveTooLarge, failure.reason)
    }

    @Test
    fun cancellationWhilePreparingDoesNotLeaveAnUnownedImport() = runTest {
        val importing = Job()
        val archive = validArchive("cancelled-skill", "Cancelled")
        val reader = SkillArchiveReader(OkHttpClient()) {
            importing.cancel()
            ByteArrayInputStream(archive)
        }
        val repository = repository(reader, this)
        try {
            withContext(importing) {
                repository.prepareImport(SkillImportSource.LocalDocument("content://cancelled"))
            }
            org.junit.Assert.fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertTrue(File(root, "staging").listFiles().orEmpty().isEmpty())
        assertTrue(File(root, "skills").listFiles().orEmpty().isEmpty())
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
    fun setEnabledTogglesObservedStateAndSurvivesRepositoryRecreation() = runTest {
        val enabledStore = FakeEnabledStore()
        val archive = validArchive("toggle-skill", "Toggle skill")
        val reader = SkillArchiveReader(
            okHttpClient = OkHttpClient(),
            localDocumentOpener = { ByteArrayInputStream(archive) },
        )
        val repository = FileSkillRepository(
            store = SkillArchiveStore(File(root, "skills"), File(root, "staging")),
            archiveReader = reader,
            enabledStore = enabledStore,
            scope = this,
        )
        val prepared = repository.prepareImport(SkillImportSource.LocalDocument("content://toggle"))
        repository.commitImport(prepared.id, allowReplace = false)

        repository.setEnabled("toggle-skill", enabled = false)
        assertEquals(false, awaitInstalled(repository).single().enabled)

        // 新实例从持久化状态合并，验证禁用状态在进程重启后仍然生效。
        val recreated = FileSkillRepository(
            store = SkillArchiveStore(File(root, "skills"), File(root, "staging")),
            archiveReader = reader,
            enabledStore = enabledStore,
            scope = this,
        )
        assertEquals(false, awaitInstalled(recreated).single().enabled)

        recreated.setEnabled("toggle-skill", enabled = true)
        assertEquals(true, awaitInstalled(recreated).single().enabled)
    }

    @Test
    fun removalClearsEnabledStateSoReinstallationDefaultsToEnabled() = runTest {
        val enabledStore = FakeEnabledStore()
        val archive = validArchive("removal-skill", "Removal skill")
        val reader = SkillArchiveReader(
            okHttpClient = OkHttpClient(),
            localDocumentOpener = { ByteArrayInputStream(archive) },
        )
        val repository = FileSkillRepository(
            store = SkillArchiveStore(File(root, "skills"), File(root, "staging")),
            archiveReader = reader,
            enabledStore = enabledStore,
            scope = this,
        )
        val prepared = repository.prepareImport(SkillImportSource.LocalDocument("content://removal"))
        repository.commitImport(prepared.id, allowReplace = false)
        repository.setEnabled("removal-skill", enabled = false)
        repository.remove("removal-skill")

        assertTrue(enabledStore.disabledNames().isEmpty())

        val reinstalled = repository.prepareImport(SkillImportSource.LocalDocument("content://removal"))
        repository.commitImport(reinstalled.id, allowReplace = false)
        assertEquals(true, awaitInstalled(repository).single().enabled)
    }

    @Test
    fun disabledSkillIsHiddenFromRuntimeSkillSource() = runTest {
        val enabledStore = FakeEnabledStore()
        val archive = validArchive("runtime-skill", "Runtime skill")
        val reader = SkillArchiveReader(
            okHttpClient = OkHttpClient(),
            localDocumentOpener = { ByteArrayInputStream(archive) },
        )
        val repository = FileSkillRepository(
            store = SkillArchiveStore(File(root, "skills"), File(root, "staging")),
            archiveReader = reader,
            enabledStore = enabledStore,
            scope = this,
        )
        val prepared = repository.prepareImport(SkillImportSource.LocalDocument("content://runtime"))
        repository.commitImport(prepared.id, allowReplace = false)
        val source = EnabledSkillSource(
            delegate = NewFileSystemSource(File(root, "skills").absolutePath),
            disabledNames = enabledStore::disabledNames,
        )

        assertEquals("runtime-skill", source.listFrontmatters().getOrThrow().single().name)
        assertTrue(source.loadInstructions("runtime-skill").isSuccess)

        repository.setEnabled("runtime-skill", enabled = false)

        assertTrue(source.listFrontmatters().getOrThrow().isEmpty())
        assertTrue(source.loadFrontmatter("runtime-skill").isFailure)
        assertTrue(source.loadInstructions("runtime-skill").isFailure)
        assertTrue(source.loadResource("runtime-skill", "assets/data.bin").isFailure)
        assertTrue(source.listResources("runtime-skill", "assets").isFailure)

        repository.setEnabled("runtime-skill", enabled = true)
        assertEquals("runtime-skill", source.loadFrontmatter("runtime-skill").getOrThrow().name)
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
            val             repository = FileSkillRepository(
                store = SkillArchiveStore(File(root, "skills"), File(root, "staging")),
                archiveReader = reader,
                enabledStore = FakeEnabledStore(),
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

    /** 仓储的初始刷新在后台协程执行；等待列表非空后再断言，避免读取初始化前的空快照。 */
    private suspend fun TestScope.awaitInstalled(repository: SkillRepository): List<InstalledSkill> {
        var result: List<InstalledSkill> = emptyList()
        repository.observeInstalled().test {
            var state = awaitItem()
            while (state.isEmpty()) state = awaitItem()
            result = state
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    private fun repository(reader: SkillArchiveReader, scope: TestScope) = FileSkillRepository(
        store = SkillArchiveStore(File(root, "skills"), File(root, "staging")),
        archiveReader = reader,
        enabledStore = FakeEnabledStore(),
        scope = scope,
    )

    /** 模拟宿主持久化的启用状态：禁用名单跨实例保留，删除即清理。 */
    private class FakeEnabledStore : SkillEnabledStore {
        private val disabled = mutableSetOf<String>()

        override fun disabledNames(): Set<String> = disabled.toSet()

        override fun setEnabled(name: String, enabled: Boolean) {
            synchronized(this) {
                if (enabled) disabled.remove(name) else disabled.add(name)
            }
        }

        override fun forget(name: String) {
            synchronized(this) { disabled.remove(name) }
        }
    }

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
            zip.putNextEntry(ZipEntry("$name/assets/data.bin"))
            zip.write(byteArrayOf(0, -1, 7))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }
}
