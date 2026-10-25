package github.ponyhuang.gimi.data.plugin.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import github.ponyhuang.gimi.core.network.HttpFileDownloadException
import github.ponyhuang.gimi.core.network.HttpFileDownloader
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.data.plugin.PluginStorage
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.domain.plugin.model.PluginPackageInstallException
import github.ponyhuang.gimi.domain.plugin.model.PluginReleaseAsset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidPluginPackageInstallerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val asset = PluginReleaseAsset("../../test.apk", "https://example.com/test.apk", 4L, "a".repeat(64))

    @Test
    fun matchingArchiveReturnsIndependentApkFilesAndForwardsIntegrityAndProgress() = runTest {
        val directory = temporaryFolder.newFolder()
        val installer = installer(directory)
        var progress = 0f
        val first = installer.downloadAndVerify(asset, "plugin.test", 61L) { progress = it }
        val second = installer.downloadAndVerify(asset, "plugin.test", 61L) {}
        assertEquals(0.42f, progress)
        assertTrue(File(first).isFile)
        assertTrue(File(second).isFile)
        assertNotEquals(first, second)
        assertEquals(directory.canonicalFile, File(first).canonicalFile.parentFile)
    }

    @Test
    fun wrongPackageVersionOrUnparseableArchiveIsDeleted() = runTest {
        for ((name, version) in listOf("other.plugin" to 61L, "plugin.test" to 60L, null to 61L)) {
            val directory = temporaryFolder.newFolder()
            assertFailure(PluginInstallFailure.INVALID_PACKAGE) {
                installer(directory, name, version).downloadAndVerify(asset, "plugin.test", 61L) {}
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun legacyInstallWithoutPublishedVersionStillChecksPackage() = runTest {
        val directory = temporaryFolder.newFolder()
        val path = installer(directory, archiveVersion = 5L).downloadAndVerify(asset, "plugin.test", null) {}
        assertTrue(File(path).isFile)
    }

    @Test
    fun sizeMismatchIsRejectedAndCleanedUp() = runTest {
        val directory = temporaryFolder.newFolder()
        assertFailure(PluginInstallFailure.CHECKSUM_MISMATCH) {
            installer(directory).downloadAndVerify(asset.copy(sizeBytes = 5L), "plugin.test", 61L) {}
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun downloadFailuresPreserveReasonAndCleanUp() = runTest {
        for ((reason, expected) in listOf(
            HttpFileDownloadException.Reason.Network to PluginInstallFailure.NETWORK,
            HttpFileDownloadException.Reason.ChecksumMismatch to PluginInstallFailure.CHECKSUM_MISMATCH,
        )) {
            val directory = temporaryFolder.newFolder()
            assertFailure(expected) {
                installer(directory, error = HttpFileDownloadException(reason))
                    .downloadAndVerify(asset, "plugin.test", 61L) {}
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun cancellationPropagatesAndRemovesPartialDownload() = runTest {
        val directory = temporaryFolder.newFolder()
        try {
            installer(directory, error = CancellationException()).downloadAndVerify(asset, "plugin.test", 61L) {}
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun invalidDownloadUrlAndOversizedMetadataFailBeforeWriting() = runTest {
        val directory = temporaryFolder.newFolder()
        val installer = installer(directory)
        assertFailure(PluginInstallFailure.NETWORK) {
            installer.downloadAndVerify(asset.copy(downloadUrl = "http://example.com/test.apk"), "plugin.test", 61L) {}
        }
        assertFailure(PluginInstallFailure.PACKAGE_TOO_LARGE) {
            installer.downloadAndVerify(asset.copy(sizeBytes = 50L * 1024 * 1024 + 1), "plugin.test", 61L) {}
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    private fun installer(
        directory: File,
        archivePackage: String? = "plugin.test",
        archiveVersion: Long = 61L,
        error: Exception? = null,
    ): AndroidPluginPackageInstaller {
        val context = mockk<Context>()
        val packages = mockk<PackageManager>()
        val archive = archivePackage?.let { name ->
            mockk<PackageInfo>().also {
                it.packageName = name
                every { it.longVersionCode } returns archiveVersion
            }
        }
        every { context.packageManager } returns packages
        every { packages.getPackageArchiveInfo(any(), 0) } returns archive
        val storage = mockk<StorageRegistry>()
        every { storage.resolve(PluginStorage.PACKAGES_ID, create = true) } returns directory
        val downloader = mockk<HttpFileDownloader>()
        coEvery { downloader.download(any(), any(), any(), any(), any()) } coAnswers {
            assertEquals(asset.downloadUrl, firstArg<String>())
            assertEquals(asset.sha256, arg<String?>(2))
            arg<File>(3).writeText("test")
            arg<(Float) -> Unit>(4)(0.42f)
            error?.let { throw it }
        }
        return AndroidPluginPackageInstaller(context, downloader, storage)
    }

    private suspend fun assertFailure(expected: PluginInstallFailure, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (error: PluginPackageInstallException) {
            assertEquals(expected, error.failure)
        }
    }
}
