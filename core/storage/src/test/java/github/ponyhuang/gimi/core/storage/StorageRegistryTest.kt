package github.ponyhuang.gimi.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageRegistryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun returnsRegisteredDirectoryById() {
        val spec = spec(id = "skills.installed", relativePath = "skills/installed")
        val registry = StorageRegistry(setOf(spec), resolver())

        assertSame(spec, registry.requireSpec("skills.installed"))
        assertEquals(1, registry.specs.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateIds() {
        StorageRegistry(
            setOf(
                spec(id = "shared.id", relativePath = "one/path"),
                spec(id = "shared.id", relativePath = "another/path", owner = "other"),
            ),
            resolver(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicatePhysicalLocations() {
        StorageRegistry(
            setOf(
                spec(id = "first.path", relativePath = "shared/path"),
                spec(id = "second.path", relativePath = "shared/path", owner = "other"),
            ),
            resolver(),
        )
    }

    private fun resolver() = FileSystemAppDirectoryResolver(
        StorageRoots(
            files = temporaryFolder.newFolder(),
            cache = temporaryFolder.newFolder(),
            codeCache = temporaryFolder.newFolder(),
            externalFiles = temporaryFolder.newFolder(),
        ),
    )

    private fun spec(
        id: String,
        relativePath: String,
        owner: String = "owner",
    ) = ManagedDirectorySpec(
        id = id,
        owner = owner,
        area = StorageArea.FILES,
        relativePath = relativePath,
        lifecycle = StorageLifecycle.PERSISTENT,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}
