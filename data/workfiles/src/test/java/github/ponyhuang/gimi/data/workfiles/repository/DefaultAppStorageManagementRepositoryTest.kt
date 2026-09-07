package github.ponyhuang.gimi.data.workfiles.repository

import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.FileSystemAppDirectoryResolver
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.ManagedStorageService
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import github.ponyhuang.gimi.core.storage.StorageMaintenanceHandler
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.core.storage.StorageRoots
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultAppStorageManagementRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadSummaryMapsLifecycleTotalsAndUnreadableCount() = runTest {
        val fixture = fixture()
        fixture.registry.resolve("test.saved", create = true).resolve("saved.bin").writeBytes(ByteArray(5))
        fixture.registry.resolve("test.cache", create = true).resolve("cache.bin").writeBytes(ByteArray(3))
        fixture.registry.resolve("test.temporary", create = true).resolve("temp.bin").writeBytes(ByteArray(2))

        val summary = fixture.repository.loadSummary()

        assertEquals(10L, summary.totalBytes)
        assertEquals(5L, summary.persistentBytes)
        assertEquals(3L, summary.cacheBytes)
        assertEquals(2L, summary.temporaryBytes)
    }

    @Test
    fun clearReclaimableProtectsPersistentDataAndReportsReclaimedBytes() = runTest {
        val fixture = fixture()
        fixture.registry.resolve("test.saved", create = true).resolve("saved.bin").writeBytes(ByteArray(5))
        fixture.registry.resolve("test.cache", create = true).resolve("cache.bin").writeBytes(ByteArray(3))
        fixture.registry.resolve("test.temporary", create = true).resolve("temp.bin").writeBytes(ByteArray(2))

        val result = fixture.repository.clearReclaimable()

        assertEquals(2, result.clearedDirectoryCount)
        assertEquals(0, result.failedDirectoryCount)
        assertEquals(5L, result.reclaimedBytes)
        assertEquals(5L, fixture.registry.resolve("test.saved").resolve("saved.bin").length())
    }

    private fun fixture(): Fixture {
        val registry = StorageRegistry(
            contributedSpecs = setOf(
                spec("test.saved", "saved", StorageLifecycle.PERSISTENT),
                spec("test.cache", "cache", StorageLifecycle.CACHE),
                spec("test.temporary", "temporary", StorageLifecycle.TEMPORARY),
            ),
            resolver = FileSystemAppDirectoryResolver(
                StorageRoots(
                    files = temporaryFolder.newFolder("files"),
                    cache = temporaryFolder.newFolder("cache-root"),
                    codeCache = temporaryFolder.newFolder("code"),
                    externalFiles = null,
                ),
            ),
        )
        val service = ManagedStorageService(
            registry = registry,
            handlers = setOf(DeletingHandler),
            ioDispatcher = Dispatchers.Unconfined,
        )
        return Fixture(registry, DefaultAppStorageManagementRepository(service))
    }

    private fun spec(id: String, path: String, lifecycle: StorageLifecycle) = ManagedDirectorySpec(
        id = id,
        owner = "test",
        area = if (lifecycle == StorageLifecycle.PERSISTENT) StorageArea.FILES else StorageArea.CACHE,
        relativePath = path,
        lifecycle = lifecycle,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )

    private object DeletingHandler : StorageMaintenanceHandler {
        override val owner = "test"

        override suspend fun clear(spec: ManagedDirectorySpec, directory: File): Boolean {
            val deleted = directory.deleteRecursively()
            return deleted && directory.mkdirs()
        }
    }

    /** Storage test fixture. */
    private data class Fixture(
        val registry: StorageRegistry,
        val repository: DefaultAppStorageManagementRepository,
    )
}
