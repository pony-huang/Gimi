package github.ponyhuang.gimi.data.workspace

import github.ponyhuang.gimi.core.storage.FileSystemAppDirectoryResolver
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.core.storage.StorageRoots
import github.ponyhuang.gimi.core.storage.workspaceDirectorySpec
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFileType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidWorkspaceRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newRepository(): Pair<AndroidWorkspaceRepository, File> {
        val filesRoot = temporaryFolder.root
        val resolver = FileSystemAppDirectoryResolver(
            StorageRoots(
                files = filesRoot,
                cache = temporaryFolder.newFolder("cache"),
                codeCache = temporaryFolder.newFolder("code-cache"),
                externalFiles = null,
            ),
        )
        val repository = AndroidWorkspaceRepository(
            StorageRegistry(setOf(workspaceDirectorySpec), resolver),
        )
        return repository to File(filesRoot, "workspace")
    }

    @Test
    fun listsWorkspaceFilesNewestFirstAndHidesStagingTmpFiles() = runBlocking {
        val (repository, root) = newRepository()
        File(root, "b-older.jpg").apply { writeBytes(byteArrayOf(1)) }.setLastModified(1_000L)
        File(root, "a-newer.pdf").apply { writeBytes(byteArrayOf(1, 2)) }.setLastModified(2_000L)
        File(root, "photo.jpg.abcdefgh1234.tmp").apply { writeBytes(byteArrayOf(9)) }

        val files = repository.list()

        assertEquals(listOf("a-newer.pdf", "b-older.jpg"), files.map { it.name })
        assertEquals(WorkspaceFileType.DOCUMENT, files[0].type)
        assertEquals(WorkspaceFileType.IMAGE, files[1].type)
        assertEquals(2L, files[0].sizeBytes)
    }

    @Test
    fun totalBytesSumsEveryWorkspaceFile() = runBlocking {
        val (repository, root) = newRepository()
        File(root, "one.bin").writeBytes(ByteArray(10))
        File(root, "two.bin").writeBytes(ByteArray(25))
        File(root, "staging.tmp").writeBytes(ByteArray(100))

        assertEquals(35L, repository.totalBytes())
    }

    @Test
    fun deleteRemovesWorkspaceFileUnderRoot() = runBlocking {
        val (repository, root) = newRepository()
        val file = File(root, "notes-abc123.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val deleted = repository.delete(
            WorkspaceFile(
                name = file.name,
                path = file.absolutePath,
                sizeBytes = 3L,
                lastModifiedMillis = file.lastModified(),
                mimeType = "application/pdf",
            ),
        )

        assertTrue(deleted)
        assertFalse(file.exists())
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun deleteRefusesPathsOutsideWorkspaceRoot() = runBlocking {
        val (repository, _) = newRepository()
        val outside = temporaryFolder.newFile("outside.pdf")

        val deleted = repository.delete(
            WorkspaceFile(
                name = outside.name,
                path = outside.absolutePath,
                sizeBytes = 0L,
                lastModifiedMillis = outside.lastModified(),
                mimeType = "application/pdf",
            ),
        )

        assertFalse(deleted)
        assertTrue(outside.exists())
    }

    @Test
    fun deleteReturnsFalseForAlreadyMissingFile() = runBlocking {
        val (repository, root) = newRepository()
        val missing = File(root, "gone-abc123.pdf")

        val deleted = repository.delete(
            WorkspaceFile(
                name = missing.name,
                path = missing.absolutePath,
                sizeBytes = 0L,
                lastModifiedMillis = 0L,
                mimeType = "application/pdf",
            ),
        )

        assertFalse(deleted)
    }
}
