package github.ponyhuang.gimi.data.plugin

import android.content.Context
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.plugin.model.PluginActionOutcome
import github.ponyhuang.gimi.domain.plugin.model.PluginBrowserRequest
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * 动态插件管理器 — 宿主侧唯一入口。
 *
 * 职责：
 * - 经 [PluginLoader] 加载一次插件并持有；
 * - 按 pluginId 持久化「已关闭」集合（默认全部启用，新装插件自动启用）；
 * - 暴露启停状态（[plugins]）与配置版本（[revision]），供 Agent 运行时缓存失效重建；
 * - 提供启用的插件实例（[enabledPlugins]）、工具（[enabledPluginTools]）与
 *   动态工具集（[enabledPluginToolsets]），以及配置读写
 *   （[configDescriptor]/[configValues]/[updateConfig]）。
 */
@Singleton
class PluginManager @Inject constructor(
    private val loader: PluginLoader,
    @ApplicationContext private val context: Context,
    private val configStore: PluginConfigStore,
) : PluginRepository {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var loaded: List<LoadedPlugin> = loader.load()
    private val disabledIds: MutableSet<String> =
        preferences.getStringSet(DISABLED_IDS_KEY, emptySet()).orEmpty().toMutableSet()

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _plugins = MutableStateFlow(descriptors())
    override val plugins: StateFlow<List<PluginDescriptor>> = _plugins.asStateFlow()

    override fun setEnabled(pluginId: String, enabled: Boolean) {
        if (loaded.none { it.plugin.pluginId == pluginId }) return
        val changed = if (enabled) disabledIds.remove(pluginId) else disabledIds.add(pluginId)
        if (!changed) return
        preferences.edit().putStringSet(DISABLED_IDS_KEY, disabledIds.toMutableSet()).apply()
        _plugins.value = descriptors()
        _revision.update { it + 1 }
    }

    /** 当前启用的插件实例，直接可作为 ADK [com.google.adk.kt.plugins.Plugin] 列表注入。 */
    fun enabledPlugins(): List<AgentPlugin> =
        loaded.asSequence()
            .filter { it.plugin.pluginId !in disabledIds }
            .map { it.plugin }
            .toList()

    /** 当前启用插件注入 Agent 的工具。 */
    fun enabledPluginTools(): List<BaseTool> = enabledPlugins().flatMap { it.tools() }

    /** 当前启用插件注入 Agent 的 Toolset（动态工具源，构建 Agent 时挂到 LlmAgent.toolsets）。 */
    fun enabledPluginToolsets(): List<Toolset> = enabledPlugins().flatMap { it.toolSets() }

    override suspend fun refresh(): List<String> = withContext(Dispatchers.IO) {
        val added = loader.refresh()
        if (added.isEmpty()) return@withContext emptyList()
        loaded = loader.load()
        _plugins.value = descriptors()
        // 递增 revision → Agent 运行时缓存失效，下次消息重建并带上新插件的工具/回调。
        _revision.update { it + 1 }
        added.map { it.plugin.pluginId }
    }

    override fun configDescriptor(pluginId: String): PluginConfigDescriptor? =
        loaded.firstOrNull { it.plugin.pluginId == pluginId }
            ?.plugin
            ?.config
            ?.let { config ->
                PluginConfigDescriptor(
                    fields = config.fields.map { field -> field.toDescriptor() },
                    actions = config.actions.map { action -> action.toActionDescriptor() },
                )
            }

    override suspend fun runAction(pluginId: String, actionId: String): PluginActionOutcome? {
        val plugin = loaded.firstOrNull { it.plugin.pluginId == pluginId }?.plugin ?: return null
        // 动作可能长时间挂起（如等待 OAuth 回调），切到 IO 执行。
        return withContext(Dispatchers.IO) {
            runCatching { plugin.runConfigAction(actionId) }
                .map { outcome -> PluginActionOutcome(outcome.message, outcome.success) }
                .getOrElse { error -> PluginActionOutcome(error.message ?: "Action failed", success = false) }
        }
    }

    override fun configActionBrowserRequest(pluginId: String, actionId: String): PluginBrowserRequest? =
        loaded.firstOrNull { it.plugin.pluginId == pluginId }
            ?.plugin
            ?.configActionBrowserRequest(actionId)
            ?.let { request ->
                PluginBrowserRequest(
                    authorizeUrl = request.authorizeUrl,
                    redirectBase = request.redirectBase,
                    completionScript = request.completionScript,
                    captureCookiesForUrl = request.captureCookiesForUrl,
                    desktopMode = request.desktopMode,
                )
            }

    override suspend fun completeAction(
        pluginId: String,
        actionId: String,
        redirectUrl: String,
    ): PluginActionOutcome? {
        val plugin = loaded.firstOrNull { it.plugin.pluginId == pluginId }?.plugin ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { plugin.completeConfigAction(actionId, redirectUrl) }
                .map { outcome -> PluginActionOutcome(outcome.message, outcome.success) }
                .getOrElse { error -> PluginActionOutcome(error.message ?: "Action failed", success = false) }
        }
    }

    override fun configValues(pluginId: String): Map<String, String> = configStore.valuesFor(pluginId)

    override fun updateConfig(pluginId: String, values: Map<String, String>) {
        configStore.save(pluginId, values)
        loaded.firstOrNull { it.plugin.pluginId == pluginId }?.plugin?.configure(values)
    }

    private fun descriptors(): List<PluginDescriptor> = loaded.map { loadedPlugin ->
        val plugin = loadedPlugin.plugin
        PluginDescriptor(
            id = plugin.pluginId,
            name = plugin.displayName,
            packageName = loadedPlugin.packageName,
            version = plugin.version,
            toolCount = plugin.tools().size,
            isEnabled = plugin.pluginId !in disabledIds,
        )
    }

    private companion object {
        const val PREFS_NAME: String = "plugin_state"
        const val DISABLED_IDS_KEY: String = "disabled_ids_v1"
    }
}
