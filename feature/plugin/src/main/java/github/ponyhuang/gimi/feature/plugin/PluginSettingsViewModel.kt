package github.ponyhuang.gimi.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogLoad
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogSource
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallResult
import github.ponyhuang.gimi.domain.plugin.model.isUpdateFor
import github.ponyhuang.gimi.domain.plugin.repository.PluginCatalogRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginInstallEnvironment
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginSubscriptionStore
import github.ponyhuang.gimi.domain.plugin.usecase.InstallPluginUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 插件列表：合并已安装插件与订阅目录，负责启停、安装、卸载与刷新。
 *
 * 订阅目录由用户配置的 HTTPS 地址提供；远端失败时目录仓库回退到内置目录，
 * 已安装插件始终可见。安装是单通道操作，任何进行中的下载都会阻止其他条目重复发起。
 */
@HiltViewModel
class PluginSettingsViewModel @Inject constructor(
    private val repository: PluginRepository,
    private val catalogRepository: PluginCatalogRepository,
    subscriptionStore: PluginSubscriptionStore,
    private val installPlugin: InstallPluginUseCase,
    private val installEnvironment: PluginInstallEnvironment,
) : ViewModel() {

    private val catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    private val local = MutableStateFlow(LocalState())
    private var installJob: Job? = null

    private val mutableEffects = MutableSharedFlow<PluginSettingsEffect>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<PluginSettingsEffect> = mutableEffects.asSharedFlow()

    val uiState: StateFlow<PluginSettingsUiState> = combine(
        catalog,
        repository.plugins,
        local,
    ) { catalogState, installed, localState ->
        buildUiState(catalogState, installed, localState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PluginSettingsUiState(
            pendingUninstallPluginId = local.value.pendingUninstallPluginId,
        ),
    )

    init {
        refreshInstallPermission()
        viewModelScope.launch { loadCatalog(force = false) }
        // 订阅地址列表变化后立即重新拉取目录；首个值来自初始化，已经在 loadCatalog 中处理。
        viewModelScope.launch {
            subscriptionStore.urls.drop(1).collect { loadCatalog(force = true) }
        }
    }

    fun onAction(action: PluginSettingsAction) {
        when (action) {
            is PluginSettingsAction.QueryChanged ->
                local.update { it.copy(query = action.value) }

            is PluginSettingsAction.SetEnabled ->
                repository.setEnabled(action.pluginId, action.enabled)

            PluginSettingsAction.Refresh -> refresh()

            is PluginSettingsAction.Install -> install(action.entryKey, update = false)

            is PluginSettingsAction.Update -> install(action.entryKey, update = true)

            is PluginSettingsAction.RequestUninstall -> requestUninstall(action.pluginId)

            PluginSettingsAction.DismissUninstall ->
                local.update { it.copy(pendingUninstallPluginId = null) }

            is PluginSettingsAction.ConfirmUninstall -> confirmUninstall(action.pluginId)
        }
    }

    private suspend fun loadCatalog(force: Boolean) {
        if (catalog.value !is CatalogState.Ready) {
            catalog.value = CatalogState.Loading
        }
        val load = try {
            catalogRepository.load(force = force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PluginCatalogLoad(
                entries = emptyList(),
                source = PluginCatalogSource.BUNDLED,
                error = true,
            )
        }
        catalog.value = CatalogState.Ready(load)
    }

    private fun refresh() {
        if (local.value.isRefreshing) return
        viewModelScope.launch {
            local.update { it.copy(isRefreshing = true) }
            refreshInstallPermission()
            try {
                val knownIds = repository.plugins.value.mapTo(mutableSetOf()) { it.id }
                // 同包覆盖更新也会返回变化 id，不能误报为发现新插件。
                val added = repository.refresh().filterNot { it in knownIds }
                if (added.isNotEmpty()) {
                    mutableEffects.emit(PluginSettingsEffect.ShowPluginAdded(added))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 已安装列表刷新失败不阻断目录浏览；系统安装广播仍会自动重新发现。
            }
            try {
                loadCatalog(force = true)
            } finally {
                local.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun refreshInstallPermission() {
        val canInstall = runCatching { installEnvironment.canRequestPackageInstalls() }
            .getOrDefault(false)
        local.update { it.copy(canInstallPackages = canInstall) }
    }

    private fun install(entryKey: String, update: Boolean) {
        if (installJob?.isActive == true) return
        val entry = (catalog.value as? CatalogState.Ready)?.load?.entries
            ?.firstOrNull { it.key == entryKey } ?: return
        val installed = repository.plugins.value.firstOrNull { it.id == entryKey }
        if (update) {
            if (installed == null || !entry.isUpdateFor(installed)) return
        } else if (installed != null) return
        if (!installEnvironment.canRequestPackageInstalls()) {
            local.update { it.copy(canInstallPackages = false) }
            viewModelScope.launch { mutableEffects.emit(PluginSettingsEffect.OpenUnknownSourcesSettings) }
            return
        }
        installJob = viewModelScope.launch {
            setInstallState(entryKey, PluginInstallUiState.Resolving)
            val result = installPlugin(entry, installed) { progress ->
                setInstallState(
                    entryKey,
                    PluginInstallUiState.Downloading(progress.coerceIn(0f, 1f)),
                )
            }
            when (result) {
                is PluginInstallResult.Ready -> {
                    val contentUri = installEnvironment.apkContentUri(result.apkPath)
                    if (contentUri == null) {
                        setInstallState(
                            entryKey,
                            PluginInstallUiState.Failed(PluginInstallFailure.STORAGE),
                        )
                    } else {
                        setInstallState(entryKey, PluginInstallUiState.Idle)
                        mutableEffects.emit(PluginSettingsEffect.LaunchInstaller(contentUri))
                    }
                }

                is PluginInstallResult.Failed -> setInstallState(
                    entryKey,
                    PluginInstallUiState.Failed(result.failure),
                )
            }
        }
    }

    private fun requestUninstall(pluginId: String) {
        if (repository.plugins.value.any { it.id == pluginId }) {
            local.update { it.copy(pendingUninstallPluginId = pluginId) }
        }
    }

    private fun confirmUninstall(pluginId: String) {
        local.update { it.copy(pendingUninstallPluginId = null) }
        val packageName = repository.plugins.value
            .firstOrNull { it.id == pluginId }
            ?.packageName
            ?: return
        viewModelScope.launch {
            mutableEffects.emit(PluginSettingsEffect.RequestSystemUninstall(packageName))
        }
    }

    private fun setInstallState(entryKey: String, state: PluginInstallUiState) {
        local.update { current ->
            val next = current.installStates.toMutableMap()
            if (state is PluginInstallUiState.Idle) {
                next.remove(entryKey)
            } else {
                next[entryKey] = state
            }
            current.copy(installStates = next)
        }
    }

    private fun buildUiState(
        catalogState: CatalogState,
        installed: List<PluginDescriptor>,
        localState: LocalState,
    ): PluginSettingsUiState {
        val catalogLoad = (catalogState as? CatalogState.Ready)?.load
        val catalogEntries = catalogLoad?.entries.orEmpty()
        val installedByKey = installed.associateBy { it.id }
        val catalogKeys = catalogEntries.mapTo(mutableSetOf()) { it.key }

        val catalogItems = catalogEntries.map { entry ->
            PluginListItemUi(
                catalog = entry,
                installed = installedByKey[entry.key],
                installState = localState.installStates[entry.key] ?: PluginInstallUiState.Idle,
            )
        }
        val installedOnlyItems = installed
            .filterNot { it.id in catalogKeys }
            .map { descriptor ->
                PluginListItemUi(
                    catalog = null,
                    installed = descriptor,
                    installState = PluginInstallUiState.Idle,
                )
            }
        val allItems = (catalogItems + installedOnlyItems).sortedWith(
            compareByDescending<PluginListItemUi> { it.isInstalled }
                .thenBy { it.catalog?.sortOrder ?: Int.MAX_VALUE }
                .thenBy { it.title },
        )
        val normalizedQuery = localState.query.trim().lowercase()
        val visibleItems = if (normalizedQuery.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.matches(normalizedQuery) }
        }
        return PluginSettingsUiState(
            items = visibleItems,
            query = localState.query,
            isRefreshing = localState.isRefreshing,
            isLoadingCatalog = catalogState is CatalogState.Loading,
            catalogSource = catalogLoad?.source ?: PluginCatalogSource.BUNDLED,
            catalogError = catalogLoad?.error == true,
            canRequestPackageInstalls = localState.canInstallPackages,
            installingEntryKey = allItems.firstOrNull { it.installState.isInProgress }?.key,
            pendingUninstallPluginId = localState.pendingUninstallPluginId,
        )
    }

    private fun PluginListItemUi.matches(query: String): Boolean {
        if (title.lowercase().contains(query)) return true
        val catalogEntry = catalog ?: return false
        return buildString {
            append(catalogEntry.key).append(' ')
            append(catalogEntry.summary.zh).append(' ')
            append(catalogEntry.summary.en).append(' ')
            catalogEntry.tags.forEach { append(it).append(' ') }
        }.lowercase().contains(query)
    }

    private sealed interface CatalogState {
        data object Loading : CatalogState
        data class Ready(val load: PluginCatalogLoad) : CatalogState
    }

    private data class LocalState(
        val query: String = "",
        val isRefreshing: Boolean = false,
        val installStates: Map<String, PluginInstallUiState> = emptyMap(),
        val canInstallPackages: Boolean = true,
        val pendingUninstallPluginId: String? = null,
    )
}
