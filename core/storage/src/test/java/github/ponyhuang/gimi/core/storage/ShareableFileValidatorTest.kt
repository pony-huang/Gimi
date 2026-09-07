package github.ponyhuang.gimi.core.storage

import android.net.Uri
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShareableFileValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsFileInsideFileProviderDirectory() {
        val fixture = fixture()
        val file = File(fixture.registry.resolve("camera.shareable", create = true), "capture.jpg")

        assertEquals(file.canonicalFile, fixture.validator.requireShareable(file))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFileInsidePrivateDirectory() {
        val fixture = fixture()
        val file = File(fixture.registry.resolve("private.cache", create = true), "secret.bin")

        fixture.validator.requireShareable(file)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFileOutsideRegistry() {
        fixture().validator.requireShareable(temporaryFolder.newFile("outside.bin"))
    }

    @Test
    fun uriFactoryValidatesThenDelegatesShareableFile() {
        val fixture = fixture()
        val file = File(fixture.registry.resolve("camera.shareable", create = true), "capture.jpg")
        val expected = mockk<Uri>()
        val factory = ShareableFileUriFactory(fixture.validator) { validated ->
            assertEquals(file.canonicalFile, validated)
            expected
        }

        assertSame(expected, factory.uriFor(file))
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
                spec("camera.shareable", "shareable/camera", SharingPolicy.FILE_PROVIDER),
                spec("private.cache", "private/cache", SharingPolicy.PRIVATE),
            ),
            FileSystemAppDirectoryResolver(roots),
        )
        return Fixture(registry, ShareableFileValidator(registry))
    }

    private fun spec(id: String, path: String, sharing: SharingPolicy) = ManagedDirectorySpec(
        id = id,
        owner = "test",
        area = StorageArea.CACHE,
        relativePath = path,
        lifecycle = StorageLifecycle.TEMPORARY,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = sharing,
    )

    /** 受测目录 Registry 和共享验证器。 */
    private data class Fixture(
        val registry: StorageRegistry,
        val validator: ShareableFileValidator,
    )
}
