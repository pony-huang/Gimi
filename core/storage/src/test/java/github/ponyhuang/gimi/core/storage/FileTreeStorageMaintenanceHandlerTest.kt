package github.ponyhuang.gimi.core.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTreeStorageMaintenanceHandlerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun clearDeletesChildrenButKeepsManagedRoot() = runTest {
        val root = temporaryFolder.newFolder("managed")
        val child = root.resolve("nested/file.bin")
        child.parentFile?.mkdirs()
        child.writeText("content")
        val handler = FileTreeStorageMaintenanceHandler("owner")

        val result = handler.clear(spec(), root)

        assertTrue(result)
        assertTrue(root.isDirectory)
        assertFalse(child.exists())
        assertEquals("owner", handler.owner)
    }

    private fun spec() = ManagedDirectorySpec(
        id = "owner.cache",
        owner = "owner",
        area = StorageArea.CACHE,
        relativePath = "owner/cache",
        lifecycle = StorageLifecycle.CACHE,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}
