package github.ponyhuang.gimi.data.environment

import github.ponyhuang.gimi.core.storage.AppDirectoryResolver

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.net.Uri
import android.os.Bundle
import io.mockk.*
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class RuntimeVersionDetectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>()
    private val packages = mockk<PackageManager>()
    private val assets = mockk<AssetManager>()
    private val resolver = mockk<ContentResolver>()
    private lateinit var repository: CompanionEnvironmentRepository
    private fun info(code: Long, api: Int = 1) = PackageInfo().apply {
        packageName = RUNTIME_PACKAGE
        setLongVersionCode(code)
        versionName = "version-$code"
        applicationInfo = ApplicationInfo().apply {
            metaData = Bundle().apply { putInt("github.ponyhuang.gimi.environment.API_VERSION", api) }
        }
    }
    @Before fun setUp() {
        every { context.cacheDir } returns temporary.root
        every { context.packageManager } returns packages
        every { context.assets } returns assets
        every { context.contentResolver } returns resolver
        every { assets.open("environment-runtime.apk") } answers { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        every { packages.getPackageArchiveInfo(any(), PackageManager.GET_META_DATA) } returns info(66)
        every { packages.getPackageInfo(RUNTIME_PACKAGE, PackageManager.GET_META_DATA) } returns info(65)
        every { resolver.call(any<Uri>(), "status", null, any()) } returns Bundle().apply {
            putBoolean("installed", true); putBoolean("enabled", true)
        }
        val directories = mockk<AppDirectoryResolver>()
        every { directories.resolve(any(), any()) } returns temporary.root
        repository = CompanionEnvironmentRepository(context, directories)
    }
    @Test fun versionsAreDetectedAndBundledApkIsExtractedOnce() = runTest {
        val state = repository.status()
        assertTrue(state.installed)
        assertTrue(state.enabled)
        assertTrue(state.updateAvailable)
        assertFalse(state.updateRequired)
        assertEquals(65L, state.runtimeVersion?.code)
        repository.status()
        verify(exactly = 1) { assets.open(any()) }
    }
    @Test fun installerApkIsRecreatedAfterCacheEviction() = runTest {
        repository.status()
        val path = repository.companionApk()
        assertTrue(java.io.File(path).delete())
        assertTrue(java.io.File(repository.companionApk()).isFile)
    }
    @Test fun missingBundledApkDegradesStatusInsteadOfFailing() = runTest {
        every { assets.open("environment-runtime.apk") } throws java.io.FileNotFoundException("environment-runtime.apk")
        every { packages.getPackageInfo(RUNTIME_PACKAGE, PackageManager.GET_META_DATA) } throws PackageManager.NameNotFoundException()
        val state = repository.status()
        assertFalse(state.available)
        assertNull(state.bundledRuntimeVersion)
        // 状态轮询不能重复读 assets，也不能把资源缺失变成异常。
        repository.status()
        verify(exactly = 1) { assets.open(any()) }
    }
    @Test fun missingBundledApkKeepsInstalledRuntimeUsable() = runTest {
        every { assets.open("environment-runtime.apk") } throws java.io.FileNotFoundException("environment-runtime.apk")
        val state = repository.status()
        assertTrue(state.available)
        assertTrue(state.runtimeUsable)
        assertFalse(state.updateAvailable)
        assertNull(state.bundledRuntimeVersion)
    }
    @Test fun invalidBundledArchiveOnlyClearsUpdateMetadata() = runTest {
        every { packages.getPackageArchiveInfo(any(), PackageManager.GET_META_DATA) } returns null
        val state = repository.status()
        assertTrue(state.available)
        assertNull(state.bundledRuntimeVersion)
        assertFalse(state.updateAvailable)
    }
    @Test fun explicitInstallReportsMissingBundledApkWithStableMessage() = runTest {
        every { assets.open("environment-runtime.apk") } throws java.io.FileNotFoundException("environment-runtime.apk")
        repository.status()
        try {
            repository.companionApk()
            fail("A missing bundled APK must fail explicit installation.")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("Bundled runtime APK is unavailable"))
        }
    }
    @Test fun absentPackageStillProvidesBundledVersion() = runTest {
        every { packages.getPackageInfo(RUNTIME_PACKAGE, PackageManager.GET_META_DATA) } throws PackageManager.NameNotFoundException()
        val state = repository.status()
        assertFalse(state.available)
        assertEquals(66L, state.bundledRuntimeVersion?.code)
    }
    @Test fun legacyProviderFailureStillOffersUpdate() = runTest {
        every { packages.getPackageInfo(RUNTIME_PACKAGE, PackageManager.GET_META_DATA) } returns info(66, 0)
        every { resolver.call(any<Uri>(), "status", null, any()) } throws IllegalArgumentException("Unknown authority")
        val state = repository.status()
        assertTrue(state.available)
        assertTrue(state.updateRequired)
        assertTrue(state.updateAvailable)
    }
    @Test fun optionalUpdateRemainsAvailableWhenProviderIsUnavailable() = runTest {
        every { resolver.call(any<Uri>(), "status", null, any()) } throws IllegalArgumentException("Unknown authority")
        val state = repository.status()
        assertTrue(state.updateAvailable)
        assertFalse(state.updateRequired)
    }
    @Test fun compatibleProviderFailureMarksStateUnknown() = runTest {
        every { packages.getPackageInfo(RUNTIME_PACKAGE, PackageManager.GET_META_DATA) } returns info(66)
        every { resolver.call(any<Uri>(), "status", null, any()) } throws IllegalArgumentException("Unknown authority")
        val state = repository.status()
        assertFalse(state.connectionAvailable)
        assertTrue(state.available)
        assertEquals(66L, state.runtimeVersion?.code)
    }
    @Test fun permissionFailurePreservesComponentMetadata() = runTest {
        every { resolver.call(any<Uri>(), "status", null, any()) } throws SecurityException("background blocked")
        val state = repository.status()
        assertFalse(state.connectionAvailable)
        assertFalse(state.runtimeUsable)
        assertTrue(state.available)
    }
    @Test fun runtimeReportsItsOwnBackgroundRestriction() = runTest {
        every { resolver.call(any<Uri>(), "status", null, any()) } returns Bundle().apply {
            putBoolean("installed", true)
            putBoolean("backgroundRestricted", true)
        }
        assertEquals(true, repository.status().backgroundRestricted)
    }
    @Test fun incompatibleRuntimeCannotExecute() = runTest {
        every { packages.getPackageInfo(RUNTIME_PACKAGE, PackageManager.GET_META_DATA) } returns info(65, 0)
        try { repository.execute("session", "echo probe", 1000); fail("Expected failure") } catch (_: IOException) { }
        verify(exactly = 0) { resolver.call(any<Uri>(), "execute", any(), any()) }
    }
}
