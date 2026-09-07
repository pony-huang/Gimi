package github.ponyhuang.gimi.core.storage

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManagedStorageServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun snapshotReportsEachDirectoryAndCategoryTotals() = runTest {
        val fixture = fixture()
        fixture.registry.resolve("cache.preview", create = true)
            .resolve("one.bin")
            .writeBytes(ByteArray(3))
        fixture.registry.resolve("files.saved", create = true)
            .resolve("two.bin")
            .writeBytes(ByteArray(5))

        val snapshot = fixture.service.snapshot()

        assertEquals(2, snapshot.directories.size)
        assertEquals(3L, snapshot.bytesByLifecycle.getValue(StorageLifecycle.CACHE))
        assertEquals(5L, snapshot.bytesByLifecycle.getValue(StorageLifecycle.PERSISTENT))
        assertTrue(snapshot.directories.all { it.status == DirectoryReadStatus.AVAILABLE })
    }

    @Test
    fun clearRejectsPersistentDirectoryWithoutCallingOwner() = runTest {
        val fixture = fixture()

        val result = fixture.service.clear("files.saved")

        assertEquals(StorageClearResult.PROTECTED, result)
        assertFalse(fixture.handler.called)
    }

    @Test
    fun clearDelegatesCacheDirectoryToItsOwner() = runTest {
        val fixture = fixture()
        fixture.registry.resolve("cache.preview", create = true).resolve("temp").writeText("x")

        val result = fixture.service.clear("cache.preview")

        assertEquals(StorageClearResult.CLEARED, result)
        assertTrue(fixture.handler.called)
    }

    private fun fixture(): Fixture {
        val roots = StorageRoots(
            files = temporaryFolder.newFolder("files"),
            cache = temporaryFolder.newFolder("cache"),
            codeCache = temporaryFolder.newFolder("code"),
            externalFiles = null,
        )
        val registry = StorageRegistry(
            setOf(
                spec("cache.preview", "preview", StorageArea.CACHE, StorageLifecycle.CACHE),
                spec("files.saved", "saved", StorageArea.FILES, StorageLifecycle.PERSISTENT),
            ),
            FileSystemAppDirectoryResolver(roots),
        )
        val handler = RecordingHandler()
        val service = ManagedStorageService(
            registry = registry,
            handlers = setOf(handler),
            ioDispatcher = Dispatchers.Unconfined,
        )
        return Fixture(registry, service, handler)
    }

    private fun spec(
        id: String,
        path: String,
        area: StorageArea,
        lifecycle: StorageLifecycle,
    ) = ManagedDirectorySpec(
        id = id,
        owner = "test-owner",
        area = area,
        relativePath = path,
        lifecycle = lifecycle,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )

    private class RecordingHandler : StorageMaintenanceHandler {
        override val owner = "test-owner"
        var called = false

        override suspend fun clear(spec: ManagedDirectorySpec, directory: File): Boolean {
            called = true
            return directory.deleteRecursively()
        }
    }

    /** 受测 Registry、服务和 owner handler。 */
    private data class Fixture(
        val registry: StorageRegistry,
        val service: ManagedStorageService,
        val handler: RecordingHandler,
    )
}
