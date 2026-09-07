package github.ponyhuang.gimi.core.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDirectoryResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun resolvesNamespacedRelativePathInsideSelectedRoot() {
        val roots = roots()
        val resolver = FileSystemAppDirectoryResolver(roots)
        val spec = spec(relativePath = "conversation/attachments")

        val result = resolver.resolve(spec)

        assertEquals(File(roots.files, "conversation/attachments").canonicalFile, result)
        assertFalse(result.exists())
    }

    @Test
    fun createsDirectoryOnlyWhenRequested() {
        val resolver = FileSystemAppDirectoryResolver(roots())
        val spec = spec(relativePath = "network/http")

        val result = resolver.resolve(spec, create = true)

        assertEquals(true, result.isDirectory)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAbsolutePath() {
        FileSystemAppDirectoryResolver(roots()).resolve(spec(relativePath = "C:/outside"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalOutsideRoot() {
        FileSystemAppDirectoryResolver(roots()).resolve(spec(relativePath = "../../outside"))
    }

    @Test
    fun externalFilesFallsBackToInternalFiles() {
        val roots = roots(externalFiles = null)
        val resolver = FileSystemAppDirectoryResolver(roots)
        val spec = spec(area = StorageArea.EXTERNAL_FILES, relativePath = "agent/artifacts")

        assertEquals(File(roots.files, "agent/artifacts").canonicalFile, resolver.resolve(spec))
    }

    private fun roots(externalFiles: File? = temporaryFolder.newFolder("external")) = StorageRoots(
        files = temporaryFolder.newFolder("files"),
        cache = temporaryFolder.newFolder("cache"),
        codeCache = temporaryFolder.newFolder("code-cache"),
        externalFiles = externalFiles,
    )

    private fun spec(
        area: StorageArea = StorageArea.FILES,
        relativePath: String,
    ) = ManagedDirectorySpec(
        id = "conversation.attachments",
        owner = "conversation",
        area = area,
        relativePath = relativePath,
        lifecycle = StorageLifecycle.PERSISTENT,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}
