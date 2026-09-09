package github.ponyhuang.gimi.data.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

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

    // 监听宿主以外的插件 APK 增删（系统设置里安装/卸载、adb install 等），强制重新发现，
    // 避免 PackageManager 状态短暂滞后导致 UI 列表与运行时快照与设备实际状态不一致。
    //
    // PACKAGE_REPLACED 在某些设备上触发时系统底层 LoadedApk 缓存尚未完全刷新，
    // 立刻查询可能命中旧 APK；按 Android 插件热更新的实践，延迟 ~200ms 再 refresh 更稳妥。
    private val packageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action == Intent.ACTION_PACKAGE_ADDED ||
                action == Intent.ACTION_PACKAGE_REMOVED ||
                action == Intent.ACTION_PACKAGE_REPLACED
            ) {
                packageScope.launch { delay(REFRESH_DELAY_MS.milliseconds); refresh() }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(packageChangeReceiver, filter)
    }

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
        // 重新发现已安装插件 APK 后必须无条件把最新快照灌入内部状态：
        // - 卸载场景下 loader.refresh() 只清缓存不返回任何条目，依赖「added 非空」会跳过加载；
        // - 仅当 added 非空才刷新 loaded，会让同包名升级等场景下插件滞后进入 Agent；
        // 因此每次刷新都按全量最新发现重建 loaded 与运行时快照，让 Agent 缓存失效与 UI 列表
        // 与设备实际安装状态保持一致。
        val added = loader.refresh()
        loaded = loader.load()
        val runtimeChanged = runtimeState.replacePlugins(loaded.map { it.plugin })
        updateDescriptors()
        if (runtimeChanged) {
            // 递增 revision → Agent 运行时缓存失效，下次消息重建并带上新插件的工具/回调。
            synchronizeRevision()
        }
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

    /** 仅在描述符发生变化时回填 _plugins，避免无意义的 collect 唤醒。 */
    private fun updateDescriptors() {
        val fresh = descriptors()
        if (_plugins.value != fresh) {
            _plugins.value = fresh
        }
    }

    private fun synchronizeRevision() {
        // 始终把宿主可见的 revision 抬到运行时快照的最新值，保证 Agent 缓存键在插件
        // 增删或配置变更后立即失效。仅在数值真不一致时写入，避免无意义的 collect 唤醒。
        val target = runtime.value.revision
        if (_revision.value != target) {
            _revision.value = target
        }
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

        // PACKAGE_* 广播到 PackageManager 缓存完全刷新之间的安全延迟。
        const val REFRESH_DELAY_MS: Long = 200L
    }
}
