package github.ponyhuang.gimi.domain.plugin.usecase

import github.ponyhuang.gimi.domain.plugin.model.LocalizedText
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogEntry
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallResult
import github.ponyhuang.gimi.domain.plugin.model.PluginPackageInstallException
import github.ponyhuang.gimi.domain.plugin.model.PluginRelease
import github.ponyhuang.gimi.domain.plugin.model.PluginReleaseAsset
import github.ponyhuang.gimi.domain.plugin.repository.PluginPackageInstaller
import github.ponyhuang.gimi.domain.plugin.repository.PluginReleaseRepository
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallPluginUseCaseTest {

    @Test
    fun selectsMatchingApkAssetAndReturnsPath() = runTest {
        val releaseRepository = FakeReleaseRepository(
            release = releaseOf(
                "Gimi-plugin-zhihu-v1.apk" to "https://example.com/zhihu.apk",
                "Gimi-plugin-v2ex-v1.apk" to "https://example.com/v2ex.apk",
            ),
        )
        val installer = FakeInstaller(path = "/cache/zhihu.apk")
        val useCase = InstallPluginUseCase(releaseRepository, installer)
        var progress = 0f

        val result = useCase(entry("zhihu")) { progress = it }

        assertEquals(PluginInstallResult.Ready("/cache/zhihu.apk"), result)
        assertEquals("https://example.com/zhihu.apk", installer.lastAsset?.downloadUrl)
        assertEquals(1, installer.calls)
        assertEquals(0.5f, progress)
    }

    @Test
    fun missingAssetFailsWithoutDownloading() = runTest {
        val releaseRepository = FakeReleaseRepository(
            release = releaseOf("Gimi-plugin-spotify-v1.apk" to "https://example.com/spotify.apk"),
        )
        val installer = FakeInstaller()
        val useCase = InstallPluginUseCase(releaseRepository, installer)

        val result = useCase(entry("zhihu")) {}

        assertEquals(PluginInstallResult.Failed(PluginInstallFailure.NO_RELEASE_ASSET), result)
        assertEquals(0, installer.calls)
    }

    @Test
    fun nonApkAssetIsIgnored() = runTest {
        val releaseRepository = FakeReleaseRepository(
            release = releaseOf("Gimi-plugin-zhihu-v1.zip" to "https://example.com/zhihu.zip"),
        )
        val installer = FakeInstaller()
        val useCase = InstallPluginUseCase(releaseRepository, installer)

        val result = useCase(entry("zhihu")) {}

        assertEquals(PluginInstallResult.Failed(PluginInstallFailure.NO_RELEASE_ASSET), result)
        assertEquals(0, installer.calls)
    }

    @Test
    fun installerFailureIsMappedToResult() = runTest {
        val releaseRepository = FakeReleaseRepository(
            release = releaseOf("Gimi-plugin-zhihu-v1.apk" to "https://example.com/zhihu.apk"),
        )
        val installer = FakeInstaller(failure = PluginInstallFailure.CHECKSUM_MISMATCH)
        val useCase = InstallPluginUseCase(releaseRepository, installer)

        val result = useCase(entry("zhihu")) {}

        assertEquals(
            PluginInstallResult.Failed(PluginInstallFailure.CHECKSUM_MISMATCH),
            result,
        )
    }

    @Test
    fun directApkUrlSkipsReleaseResolution() = runTest {
        val releaseRepository = FakeReleaseRepository(error = IOException("must not be called"))
        val installer = FakeInstaller()
        val sha = "a".repeat(64)
        val useCase = InstallPluginUseCase(releaseRepository, installer)

        val result = useCase(
            entry("custom").copy(
                apkUrl = "https://example.com/custom.apk",
                sha256 = sha,
                sizeBytes = 42L,
            ),
        ) {}

        assertTrue(result is PluginInstallResult.Ready)
        assertEquals("https://example.com/custom.apk", installer.lastAsset?.downloadUrl)
        assertEquals(sha, installer.lastAsset?.sha256)
        assertEquals(42L, installer.lastAsset?.sizeBytes)
    }

    @Test
    fun releaseFailureBecomesNetworkFailure() = runTest {
        val releaseRepository = FakeReleaseRepository(error = IOException("offline"))
        val installer = FakeInstaller()
        val useCase = InstallPluginUseCase(releaseRepository, installer)

        val result = useCase(entry("zhihu")) {}

        assertEquals(PluginInstallResult.Failed(PluginInstallFailure.NETWORK), result)
        assertEquals(0, installer.calls)
    }

    @Test
    fun assetPrefixIsMatchedAsPrefixNotWholeName() = runTest {
        val releaseRepository = FakeReleaseRepository(
            release = releaseOf(
                "Gimi-plugin-zhihu-v0.13.0-arm64.apk" to "https://example.com/zhihu.apk",
            ),
        )
        val installer = FakeInstaller()
        val useCase = InstallPluginUseCase(releaseRepository, installer)

        val result = useCase(entry("zhihu")) {}

        assertTrue(result is PluginInstallResult.Ready)
        assertEquals("Gimi-plugin-zhihu-v0.13.0-arm64.apk", installer.lastAsset?.name)
    }

    private fun releaseOf(vararg assets: Pair<String, String>): PluginRelease = PluginRelease(
        tagName = "v0.13.0",
        assets = assets.map { (name, url) ->
            PluginReleaseAsset(name = name, downloadUrl = url, sizeBytes = 1024, sha256 = null)
        },
    )

    private fun entry(key: String) = PluginCatalogEntry(
        key = key,
        packageName = "github.ponyhuang.gimi.plugin.$key",
        title = key,
        summary = LocalizedText("摘要", "Summary"),
        description = LocalizedText("描述", "Description"),
        highlights = emptyList(),
        sortOrder = 0,
        pluginVersion = 1,
        releaseAssetPrefix = "Gimi-plugin-$key-v",
        repoUrl = "",
        iconKey = key,
        tags = emptyList(),
    )

    private class FakeReleaseRepository(
        private val release: PluginRelease? = null,
        private val error: Exception? = null,
    ) : PluginReleaseRepository {
        override suspend fun latestRelease(): PluginRelease {
            error?.let { throw it }
            return requireNotNull(release)
        }
    }

    private class FakeInstaller(
        private val failure: PluginInstallFailure? = null,
        private val path: String = "/cache/plugin.apk",
    ) : PluginPackageInstaller {
        var calls: Int = 0
        var lastAsset: PluginReleaseAsset? = null

        override suspend fun downloadAndVerify(
            asset: PluginReleaseAsset,
            packageName: String,
            versionCode: Long?,
            onProgress: (Float) -> Unit,
        ): String {
            calls += 1
            lastAsset = asset
            onProgress(0.5f)
            failure?.let { throw PluginPackageInstallException(it) }
            return path
        }
    }
}
