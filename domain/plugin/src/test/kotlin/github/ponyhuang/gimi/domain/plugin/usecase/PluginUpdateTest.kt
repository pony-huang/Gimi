package github.ponyhuang.gimi.domain.plugin.usecase

import github.ponyhuang.gimi.domain.plugin.model.*
import github.ponyhuang.gimi.domain.plugin.repository.PluginPackageInstaller
import github.ponyhuang.gimi.domain.plugin.repository.PluginReleaseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PluginUpdateTest {
    private val installed = PluginDescriptor("test", "Test", "plugin.test", 999, 1, false, 60L, "9.0.0")
    private val entry = PluginCatalogEntry(
        "test", "plugin.test", "Test", LocalizedText("测试", "Test"), LocalizedText("测试", "Test"),
        emptyList(), 0, 1, "Gimi-plugin-test-v", "", "test", emptyList(),
        apkUrl = "https://example.com/test.apk", versionCode = 61L, versionName = "1.0.0",
    )

    @Test
    fun onlyApkVersionCodeDeterminesUpdatesIncludingDisabledPlugins() {
        assertTrue(entry.isUpdateFor(installed))
        assertFalse(entry.copy(versionCode = 60L, pluginVersion = 1000).isUpdateFor(installed))
        assertFalse(entry.copy(versionCode = 59L, versionName = "99.0.0").isUpdateFor(installed))
        assertFalse(entry.copy(versionCode = null).isUpdateFor(installed))
        assertFalse(entry.copy(packageName = "other.plugin").isUpdateFor(installed))
        assertFalse(entry.isUpdateFor(installed.copy(versionCode = 0L)))
        assertTrue(entry.copy(versionCode = Int.MAX_VALUE.toLong() + 2).isUpdateFor(installed))
    }

    @Test
    fun obsoleteOrWrongPackageUpdateDoesNotDownloadOrResolveRelease() = runTest {
        val installer = RecordingInstaller()
        val useCase = InstallPluginUseCase(unusedRelease(), installer)
        for (candidate in listOf(entry.copy(versionCode = 60L), entry.copy(versionCode = null), entry.copy(packageName = "other"))) {
            assertEquals(PluginInstallResult.Failed(PluginInstallFailure.NOT_AN_UPDATE), useCase(candidate, installed) {})
        }
        assertNull(installer.request)
    }

    @Test
    fun updatePassesExpectedPackageAndVersionToArchiveValidation() = runTest {
        val installer = RecordingInstaller()
        val result = InstallPluginUseCase(unusedRelease(), installer)(entry, installed) {}
        assertEquals(PluginInstallResult.Ready("/cache/test.apk"), result)
        assertEquals("plugin.test" to 61L, installer.request)
    }

    @Test
    fun downloadCancellationIsNotConvertedToFailure() = runTest {
        val installer = RecordingInstaller(cancel = true)
        try {
            InstallPluginUseCase(unusedRelease(), installer)(entry, installed) {}
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertNotNull(installer.request)
        }
    }

    private fun unusedRelease() = object : PluginReleaseRepository {
        override suspend fun latestRelease(): PluginRelease = error("Direct subscriptions do not resolve releases")
    }

    private class RecordingInstaller(private val cancel: Boolean = false) : PluginPackageInstaller {
        var request: Pair<String, Long?>? = null
        override suspend fun downloadAndVerify(
            asset: PluginReleaseAsset,
            packageName: String,
            versionCode: Long?,
            onProgress: (Float) -> Unit,
        ): String {
            request = packageName to versionCode
            if (cancel) throw CancellationException()
            return "/cache/test.apk"
        }
    }
}
