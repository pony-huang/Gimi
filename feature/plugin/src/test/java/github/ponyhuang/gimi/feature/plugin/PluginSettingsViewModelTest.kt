package github.ponyhuang.gimi.feature.plugin

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.plugin.model.LocalizedText
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallback
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
import github.ponyhuang.gimi.domain.plugin.model.PluginActionOutcome
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogEntry
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogLoad
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogSource
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.domain.plugin.model.PluginPackageInstallException
import github.ponyhuang.gimi.domain.plugin.model.PluginRelease
import github.ponyhuang.gimi.domain.plugin.model.PluginReleaseAsset
import github.ponyhuang.gimi.domain.plugin.repository.PluginCatalogRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginInstallEnvironment
import github.ponyhuang.gimi.domain.plugin.repository.PluginPackageInstaller
import github.ponyhuang.gimi.domain.plugin.repository.PluginReleaseRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginSubscriptionStore
import github.ponyhuang.gimi.domain.plugin.usecase.InstallPluginUseCase
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val installedZhihu = PluginDescriptor(
        id = "zhihu",
        name = "知乎",
        packageName = "com.example.zhihu",
        version = 3,
        toolCount = 4,
        isEnabled = true,
    )

    private val defaultCatalog = PluginCatalogLoad(
        entries = listOf(
            catalogEntry("zhihu", "知乎", tags = listOf("问答", "热榜")),
            catalogEntry("spotify", "Spotify", tags = listOf("音乐")),
        ),
        source = PluginCatalogSource.REMOTE,
    )

    @Test
    fun refreshDelegatesAndEmitsAddedEffect() = runTest {
        val harness = harness(
            pluginRepository = FakePluginRepository().apply { refreshResult = listOf("spotify") },
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.Refresh)
            advanceUntilIdle()

            assertEquals(
                PluginSettingsEffect.ShowPluginAdded(listOf("spotify")),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, harness.pluginRepository.refreshCalls)
    }

    @Test
    fun refreshWithNoAdditionsEmitsNothing() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.Refresh)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(1, harness.pluginRepository.refreshCalls)
    }

    @Test
    fun refreshTogglesRefreshingIndicator() = runTest {
        val gate = CompletableDeferred<Unit>()
        val harness = harness(
            pluginRepository = FakePluginRepository().apply { refreshGate = gate },
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSettingsAction.Refresh)
        runCurrent()

        assertTrue(harness.viewModel.uiState.value.isRefreshing)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(harness.viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun loadsCatalogAndMergesInstalledPlugins() = runTest {
        val harness = harness(
            pluginRepository = FakePluginRepository().apply {
                pluginsFlow.value = listOf(installedZhihu)
            },
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        val state = harness.viewModel.uiState.value
        assertEquals(listOf("zhihu", "spotify"), state.items.map { it.key })
        assertTrue(state.items.first { it.key == "zhihu" }.isInstalled)
        assertFalse(state.items.first { it.key == "spotify" }.isInstalled)
        assertEquals(PluginCatalogSource.REMOTE, state.catalogSource)
        assertFalse(state.catalogError)
    }

    @Test
    fun installedPluginOutsideCatalogIsStillListed() = runTest {
        val custom = installedZhihu.copy(id = "custom", name = "Custom")
        val harness = harness(
            pluginRepository = FakePluginRepository().apply {
                pluginsFlow.value = listOf(custom)
            },
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        val item = harness.viewModel.uiState.value.items.first { it.key == "custom" }
        assertNull(item.catalog)
        assertTrue(item.isInstalled)
        assertEquals("Custom", item.title)
    }

    @Test
    fun queryFiltersByTitleAndTags() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSettingsAction.QueryChanged("热榜"))
        advanceUntilIdle()
        assertEquals(listOf("zhihu"), harness.viewModel.uiState.value.items.map { it.key })

        harness.viewModel.onAction(PluginSettingsAction.QueryChanged("音乐"))
        advanceUntilIdle()
        assertEquals(listOf("spotify"), harness.viewModel.uiState.value.items.map { it.key })
    }

    @Test
    fun installSuccessEmitsInstallerEffectAndResetsState() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.Install("spotify"))
            advanceUntilIdle()

            assertEquals(
                PluginSettingsEffect.LaunchInstaller("content://plugins/apk/spotify"),
                awaitItem(),
            )
        }
        val spotify = harness.viewModel.uiState.value.items.first { it.key == "spotify" }
        assertEquals(PluginInstallUiState.Idle, spotify.installState)
    }

    @Test
    fun installFailureSetsFailedState() = runTest {
        val harness = harness(
            installer = FakePackageInstaller(failure = PluginInstallFailure.CHECKSUM_MISMATCH),
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSettingsAction.Install("spotify"))
        advanceUntilIdle()

        val spotify = harness.viewModel.uiState.value.items.first { it.key == "spotify" }
        assertEquals(
            PluginInstallUiState.Failed(PluginInstallFailure.CHECKSUM_MISMATCH),
            spotify.installState,
        )
        assertNull(harness.viewModel.uiState.value.installingEntryKey)
    }

    @Test
    fun installWithoutPermissionRequestsSystemSettings() = runTest {
        val harness = harness(environment = FakeInstallEnvironment(canInstall = false))
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.Install("spotify"))
            advanceUntilIdle()
            assertEquals(PluginSettingsEffect.OpenUnknownSourcesSettings, awaitItem())
        }
        assertFalse(harness.viewModel.uiState.value.canRequestPackageInstalls)
        assertEquals(0, harness.installer.calls)
    }

    @Test
    fun subscriptionUrlChangeForcesCatalogReload() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()
        val initialLoads = harness.catalogRepository.loadCalls

        harness.subscriptionStore.add("https://example.com/other-feed.json")
        advanceUntilIdle()

        assertTrue(harness.catalogRepository.loadCalls > initialLoads)
        assertTrue(harness.catalogRepository.forcedLoads >= 1)
    }

    @Test
    fun catalogErrorIsExposedForFallbackBanner() = runTest {
        val harness = harness(
            catalogLoad = PluginCatalogLoad(
                entries = emptyList(),
                source = PluginCatalogSource.BUNDLED,
                error = true,
            ),
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        assertTrue(harness.viewModel.uiState.value.catalogError)
        assertEquals(PluginCatalogSource.BUNDLED, harness.viewModel.uiState.value.catalogSource)
    }

    @Test
    fun confirmUninstallEmitsSystemUninstallAndClearsPending() = runTest {
        val harness = harness(
            pluginRepository = FakePluginRepository().apply {
                pluginsFlow.value = listOf(installedZhihu)
            },
        )
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.RequestUninstall("zhihu"))
            harness.viewModel.onAction(PluginSettingsAction.ConfirmUninstall("zhihu"))
            advanceUntilIdle()

            assertEquals(
                PluginSettingsEffect.RequestSystemUninstall("com.example.zhihu"),
                awaitItem(),
            )
        }
        assertNull(harness.viewModel.uiState.value.pendingUninstallPluginId)
    }

    @Test
    fun requestUninstallForUnknownPluginIsIgnored() = runTest {
        val harness = harness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()

        harness.viewModel.onAction(PluginSettingsAction.RequestUninstall("missing"))
        advanceUntilIdle()

        assertNull(harness.viewModel.uiState.value.pendingUninstallPluginId)
    }

    @Test
    fun updateUsesInstalledApkVersionAndEmitsSystemInstallerWithoutChangingEnablement() = runTest {
        val harness = updateHarness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()
        assertTrue(harness.viewModel.uiState.value.items.first { it.key == "zhihu" }.isUpdateAvailable)

        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.Update("zhihu"))
            advanceUntilIdle()
            assertEquals(PluginSettingsEffect.LaunchInstaller("content://plugins/apk/zhihu"), awaitItem())
        }
        assertEquals("github.ponyhuang.gimi.plugin.zhihu" to 61L, harness.installer.lastRequest)
        assertFalse(harness.pluginRepository.plugins.value.single().isEnabled)
        harness.pluginRepository.pluginsFlow.value = harness.pluginRepository.plugins.value.map { it.copy(versionCode = 61L) }
        advanceUntilIdle()
        assertFalse(harness.viewModel.uiState.value.items.first { it.key == "zhihu" }.isUpdateAvailable)
    }

    @Test
    fun refreshedUpgradeDoesNotEmitNewPluginNotice() = runTest {
        val harness = updateHarness()
        harness.pluginRepository.refreshResult = listOf("zhihu")
        collectUiState(harness.viewModel)
        advanceUntilIdle()
        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.Refresh)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun equalOlderUnknownOrDifferentPackageUpdatesAreIgnored() = runTest {
        for (entry in listOf(
            catalogEntry("zhihu", "知乎").copy(versionCode = 60L),
            catalogEntry("zhihu", "知乎").copy(versionCode = 59L),
            catalogEntry("zhihu", "知乎").copy(versionCode = null),
            catalogEntry("zhihu", "知乎").copy(versionCode = 61L, packageName = "another.plugin"),
        )) {
            val harness = updateHarness(entry = entry)
            collectUiState(harness.viewModel)
            advanceUntilIdle()
            assertFalse(harness.viewModel.uiState.value.items.first().isUpdateAvailable)
            harness.viewModel.onAction(PluginSettingsAction.Update("zhihu"))
            advanceUntilIdle()
            assertEquals(0, harness.installer.calls)
        }
    }

    @Test
    fun updateWithoutPermissionOpensSettingsWithoutDownloading() = runTest {
        val harness = updateHarness(environment = FakeInstallEnvironment(canInstall = false))
        collectUiState(harness.viewModel)
        advanceUntilIdle()
        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.Update("zhihu"))
            advanceUntilIdle()
            assertEquals(PluginSettingsEffect.OpenUnknownSourcesSettings, awaitItem())
        }
        assertEquals(0, harness.installer.calls)
    }

    @Test
    fun updateProgressBlocksDuplicateAndOtherInstallsAndFailureCanRetry() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = FakePackageInstaller(failure = PluginInstallFailure.NETWORK).apply { downloadGate = gate }
        val harness = updateHarness(installer = installer)
        collectUiState(harness.viewModel)
        advanceUntilIdle()
        harness.viewModel.onAction(PluginSettingsAction.Update("zhihu"))
        runCurrent()
        assertEquals("zhihu", harness.viewModel.uiState.value.installingEntryKey)
        assertEquals(PluginInstallUiState.Downloading(0.8f), harness.viewModel.uiState.value.items.first().installState)
        harness.viewModel.onAction(PluginSettingsAction.Update("zhihu"))
        harness.viewModel.onAction(PluginSettingsAction.Install("spotify"))
        runCurrent()
        assertEquals(1, installer.calls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(PluginInstallUiState.Failed(PluginInstallFailure.NETWORK), harness.viewModel.uiState.value.items.first().installState)
        assertNull(harness.viewModel.uiState.value.installingEntryKey)
        installer.failure = null
        harness.viewModel.effects.test {
            harness.viewModel.onAction(PluginSettingsAction.Update("zhihu"))
            advanceUntilIdle()
            assertEquals(PluginSettingsEffect.LaunchInstaller("content://plugins/apk/zhihu"), awaitItem())
        }
        assertEquals(2, installer.calls)
    }

    @Test
    fun installActionCannotReinstallAnAlreadyInstalledPlugin() = runTest {
        val harness = updateHarness()
        collectUiState(harness.viewModel)
        advanceUntilIdle()
        harness.viewModel.onAction(PluginSettingsAction.Install("zhihu"))
        advanceUntilIdle()
        assertEquals(0, harness.installer.calls)
    }

    private fun updateHarness(
        entry: PluginCatalogEntry = catalogEntry("zhihu", "知乎").copy(versionCode = 61L, versionName = "1.1.0"),
        environment: FakeInstallEnvironment = FakeInstallEnvironment(),
        installer: FakePackageInstaller = FakePackageInstaller(),
    ) = harness(
        pluginRepository = FakePluginRepository().apply {
            pluginsFlow.value = listOf(installedZhihu.copy(
                packageName = "github.ponyhuang.gimi.plugin.zhihu", versionCode = 60L, versionName = "1.0.0", isEnabled = false,
            ))
        },
        catalogLoad = defaultCatalog.copy(entries = listOf(entry, catalogEntry("spotify", "Spotify"))),
        environment = environment,
        installer = installer,
    )

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private class Harness(
        val viewModel: PluginSettingsViewModel,
        val pluginRepository: FakePluginRepository,
        val catalogRepository: FakeCatalogRepository,
        val subscriptionStore: FakeSubscriptionStore,
        val environment: FakeInstallEnvironment,
        val installer: FakePackageInstaller,
    )

    private fun TestScope.collectUiState(viewModel: PluginSettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun harness(
        pluginRepository: FakePluginRepository = FakePluginRepository(),
        catalogLoad: PluginCatalogLoad = defaultCatalog,
        environment: FakeInstallEnvironment = FakeInstallEnvironment(),
        installer: FakePackageInstaller = FakePackageInstaller(),
    ): Harness {
        val catalogRepository = FakeCatalogRepository(catalogLoad)
        val subscriptionStore = FakeSubscriptionStore()
        val viewModel = PluginSettingsViewModel(
            repository = pluginRepository,
            catalogRepository = catalogRepository,
            subscriptionStore = subscriptionStore,
            installPlugin = InstallPluginUseCase(FakeReleaseRepository(), installer),
            installEnvironment = environment,
        )
        return Harness(
            viewModel = viewModel,
            pluginRepository = pluginRepository,
            catalogRepository = catalogRepository,
            subscriptionStore = subscriptionStore,
            environment = environment,
            installer = installer,
        )
    }

    private fun catalogEntry(
        key: String,
        title: String,
        tags: List<String> = emptyList(),
    ) = PluginCatalogEntry(
        key = key,
        packageName = "github.ponyhuang.gimi.plugin.$key",
        title = title,
        summary = LocalizedText("摘要 $title", "Summary $title"),
        description = LocalizedText("描述 $title", "Description $title"),
        highlights = emptyList(),
        sortOrder = 0,
        pluginVersion = 1,
        releaseAssetPrefix = "Gimi-plugin-$key-v",
        repoUrl = "",
        iconKey = key,
        tags = tags,
        apkUrl = "https://example.com/$key.apk",
    )

    private class FakeCatalogRepository(
        private val load: PluginCatalogLoad,
    ) : PluginCatalogRepository {
        var loadCalls: Int = 0
        var forcedLoads: Int = 0

        override suspend fun load(force: Boolean): PluginCatalogLoad {
            loadCalls += 1
            if (force) forcedLoads += 1
            return load
        }
    }

    private class FakeSubscriptionStore : PluginSubscriptionStore {
        override val defaultUrl: String = DEFAULT_URL
        private val mutableUrls = MutableStateFlow(listOf(DEFAULT_URL))
        override val urls: StateFlow<List<String>> = mutableUrls

        override fun add(url: String) {
            if (url !in mutableUrls.value) mutableUrls.value = mutableUrls.value + url
        }

        override fun remove(url: String) {
            mutableUrls.value = mutableUrls.value - url
        }

        override fun reset() {
            mutableUrls.value = listOf(DEFAULT_URL)
        }

        private companion object {
            const val DEFAULT_URL = "https://example.com/feed.json"
        }
    }

    private class FakeReleaseRepository : PluginReleaseRepository {
        override suspend fun latestRelease(): PluginRelease =
            throw IOException("direct url should bypass release resolution")
    }

    private class FakePackageInstaller(
        var failure: PluginInstallFailure? = null,
    ) : PluginPackageInstaller {
        var calls: Int = 0
        var lastRequest: Pair<String, Long?>? = null
        var downloadGate: CompletableDeferred<Unit>? = null

        override suspend fun downloadAndVerify(
            asset: PluginReleaseAsset,
            packageName: String,
            versionCode: Long?,
            onProgress: (Float) -> Unit,
        ): String {
            calls += 1
            lastRequest = packageName to versionCode
            onProgress(0.8f)
            downloadGate?.await()
            failure?.let { throw PluginPackageInstallException(it) }
            return "apk/${asset.name.substringBefore('.')}"
        }
    }

    private class FakeInstallEnvironment(
        private val canInstall: Boolean = true,
    ) : PluginInstallEnvironment {
        override fun canRequestPackageInstalls(): Boolean = canInstall
        override fun apkContentUri(apkPath: String): String = "content://plugins/$apkPath"
    }

    private class FakePluginRepository : PluginRepository {
        val pluginsFlow = MutableStateFlow<List<PluginDescriptor>>(emptyList())
        override val plugins: StateFlow<List<PluginDescriptor>> = pluginsFlow
        override val revision: StateFlow<Long> = MutableStateFlow(0L)
        var refreshCalls: Int = 0
        var refreshResult: List<String> = emptyList()
        var refreshGate: CompletableDeferred<Unit>? = null

        override fun setEnabled(pluginId: String, enabled: Boolean) = Unit

        override fun configDescriptor(pluginId: String): PluginConfigDescriptor? = null

        override fun configValues(pluginId: String): Map<String, String> = emptyMap()

        override fun updateConfig(pluginId: String, values: Map<String, String>) = Unit

        override suspend fun runAction(
            pluginId: String,
            actionId: String,
        ): PluginActionExecution? = null

        override suspend fun refresh(): List<String> {
            refreshCalls += 1
            refreshGate?.await()
            return refreshResult
        }

        override suspend fun onActionCallback(
            pluginId: String,
            actionId: String,
            callback: PluginActionCallback,
        ): PluginActionOutcome? = null
    }
}
