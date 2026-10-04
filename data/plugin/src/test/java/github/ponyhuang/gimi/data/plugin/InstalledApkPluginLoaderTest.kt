package github.ponyhuang.gimi.data.plugin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.pluginapi.PluginApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstalledApkPluginLoaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun oldApkWithoutMetadataIsSkippedBeforeAccessingPluginCode() = runTest {
        assertRejectedBeforeLoading(metadata = null)
    }

    @Test
    fun adkMismatchIsSkippedEvenWhenApiVersionMatches() = runTest {
        val metadata = mockk<Bundle>()
        every { metadata.getInt(PluginApi.API_VERSION_META_DATA_KEY) } returns PluginApi.VERSION
        every { metadata.getString(PluginApi.ADK_VERSION_META_DATA_KEY) } returns "0.8.0"
        assertRejectedBeforeLoading(metadata)
    }

    private suspend fun assertRejectedBeforeLoading(metadata: Bundle?) {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val configStore = mockk<PluginConfigStore>()
        val storageRegistry = mockk<StorageRegistry>()
        val notices = PluginLoadNoticeQueue()
        val root = temporaryFolder.newFolder("optimized")
        val flags = mockk<PackageManager.ResolveInfoFlags>()
        mockkStatic(PackageManager.ResolveInfoFlags::class)
        every { PackageManager.ResolveInfoFlags.of(0L) } returns flags
        val appInfo = ApplicationInfo().apply {
            metaData = metadata
            // 即使实现类声明/路径无效，不兼容 APK 也应先被拦截并提示升级。
            sourceDir = "/missing/plugin.apk"
        }
        val resolveInfo = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply { packageName = "plugin.old" }
        }
        every { context.packageManager } returns packageManager
        every { packageManager.queryIntentServices(any(), flags) } returns listOf(resolveInfo)
        every { packageManager.getApplicationInfo("plugin.old", PackageManager.GET_META_DATA) } returns appInfo
        every { packageManager.getApplicationLabel(appInfo) } returns "Old plugin"
        every { packageManager.getPackageInfo("plugin.old", 0) } returns PackageInfo().apply {
            lastUpdateTime = 1L
        }
        every { storageRegistry.resolve(PluginStorage.OPTIMIZED_ROOT_ID, create = true) } returns root
        val loader = InstalledApkPluginLoader(context, configStore, storageRegistry, notices)

        assertTrue(loader.load().isEmpty())
        assertEquals("Old plugin", notices.incompatiblePluginNames.first())
        assertTrue(loader.refresh().isEmpty())
        assertFalse(root.resolve("plugin.old").exists())
        verify(exactly = 0) { context.classLoader }
        verify(exactly = 0) { context.applicationContext }
        verify(exactly = 0) { configStore.valuesFor(any()) }
    }
}
