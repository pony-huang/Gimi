package github.ponyhuang.gimi.data.plugin

import android.content.Context
import androidx.core.content.edit
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallback
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallbackRequest
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
import github.ponyhuang.gimi.domain.plugin.model.PluginActionOutcome
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginConfigActionExecution
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : PluginRepository, PluginRuntimeProvider<AgentPlugin> {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var loaded: List<LoadedPlugin> = loader.load()
    private val disabledIds: MutableSet<String> =
        preferences.getStringSet(DISABLED_IDS_KEY, emptySet()).orEmpty().toMutableSet()
    private val runtimeState = PluginRuntimeState(
        initialPlugins = loaded.map { it.plugin },
        initialDisabledPluginIds = disabledIds,
        pluginId = AgentPlugin::pluginId,
    )

    override val runtime: StateFlow<PluginRuntimeSnapshot<AgentPlugin>> = runtimeState.runtime

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _plugins = MutableStateFlow(descriptors())
    override val plugins: StateFlow<List<PluginDescriptor>> = _plugins.asStateFlow()

    override fun setEnabled(pluginId: String, enabled: Boolean) {
        val changed = runtimeState.setEnabled(pluginId, enabled)
        if (!changed) return
        disabledIds.clear()
        disabledIds.addAll(runtimeState.disabledPluginIds())
        preferences.edit { putStringSet(DISABLED_IDS_KEY, disabledIds.toMutableSet()) }
        _plugins.value = descriptors()
        synchronizeRevision()
    }

    /** 当前启用的插件实例，直接可作为 ADK [com.google.adk.kt.plugins.Plugin] 列表注入。 */
    fun enabledPlugins(): List<AgentPlugin> = runtime.value.enabledPlugins

    /** 当前启用插件注入 Agent 的工具。 */
    fun enabledPluginTools(): List<BaseTool> = enabledPlugins().flatMap { it.tools() }

    /** 当前启用插件注入 Agent 的 Toolset（动态工具源，构建 Agent 时挂到 LlmAgent.toolsets）。 */
    fun enabledPluginToolsets(): List<Toolset> = enabledPlugins().flatMap { it.toolSets() }

    override suspend fun refresh(): List<String> = withContext(Dispatchers.IO) {
        val added = loader.refresh()
        if (added.isEmpty()) return@withContext emptyList()
        loaded = loader.load()
        runtimeState.replacePlugins(loaded.map { it.plugin })
        _plugins.value = descriptors()
        // 递增 revision → Agent 运行时缓存失效，下次消息重建并带上新插件的工具/回调。
        synchronizeRevision()
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

    override suspend fun runAction(pluginId: String, actionId: String): PluginActionExecution? {
        val plugin = loaded.firstOrNull { it.plugin.pluginId == pluginId }?.plugin ?: return null
        // 普通动作可能长时间挂起；需要宿主交互的动作则返回请求，由 feature 层继续驱动。
        return withContext(Dispatchers.IO) {
            try {
                plugin.runConfigAction(actionId).toDomainExecution()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                PluginActionExecution.Completed(
                    PluginActionOutcome(error.message ?: "Action failed", success = false),
                )
            }
        }
    }

    override suspend fun onActionCallback(
        pluginId: String,
        actionId: String,
        callback: PluginActionCallback,
    ): PluginActionOutcome? {
        val plugin = loaded.firstOrNull { it.plugin.pluginId == pluginId }?.plugin ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val outcome = plugin.onConfigActionCallback(
                    actionId = actionId,
                    callback = github.ponyhuang.gimi.pluginapi.PluginActionCallback(callback.values),
                )
                PluginActionOutcome(outcome.message, outcome.success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                PluginActionOutcome(error.message ?: "Action failed", success = false)
            }
        }
    }

    override fun configValues(pluginId: String): Map<String, String> = configStore.valuesFor(pluginId)

    override fun updateConfig(pluginId: String, values: Map<String, String>) {
        val previousValues = configStore.valuesFor(pluginId)
        configStore.save(pluginId, values)
        val plugin = loaded.firstOrNull { it.plugin.pluginId == pluginId }?.plugin
        plugin?.configure(values)
        if (plugin != null && previousValues != values) {
            runtimeState.markConfigurationChanged(pluginId)
            synchronizeRevision()
        }
    }

    private fun descriptors(): List<PluginDescriptor> = loaded.map { loadedPlugin ->
        val plugin = loadedPlugin.plugin
        PluginDescriptor(
            id = plugin.pluginId,
            name = plugin.displayName,
            packageName = loadedPlugin.packageName,
            version = plugin.version,
            toolCount = plugin.toolCount,
            isEnabled = runtimeState.isEnabled(plugin.pluginId),
        )
    }

    private fun synchronizeRevision() {
        _revision.value = runtime.value.revision
    }

    private fun PluginConfigActionExecution.toDomainExecution(): PluginActionExecution = when (this) {
        is PluginConfigActionExecution.Completed -> PluginActionExecution.Completed(
            PluginActionOutcome(result.message, result.success),
        )
        is PluginConfigActionExecution.AwaitingCallback -> PluginActionExecution.AwaitingCallback(
            PluginActionCallbackRequest(
                handlerId = request.handlerId,
                parameters = request.parameters,
            ),
        )
    }

    private companion object {
        const val PREFS_NAME: String = "plugin_state"
        const val DISABLED_IDS_KEY: String = "disabled_ids_v1"
    }
}
