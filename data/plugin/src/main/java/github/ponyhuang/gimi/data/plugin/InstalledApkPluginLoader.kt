package github.ponyhuang.gimi.data.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.DexClassLoader
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginApi
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * 基于「独立安装的插件 APK」的动态加载器（参考 keiyoushi/Tachiyomi 的 DCL 思路）。
 *
 * 发现协议：
 * - 插件 APK 声明一个 exported=true 的无功能 service，带
 *   intent-filter `action=[PluginApi.DISCOVERY_ACTION]`；
 * - `<application>` 下 `<meta-data android:name=[PluginApi.CLASS_META_DATA_KEY]>` 声明实现类全名；
 * - [PluginApi.API_VERSION_META_DATA_KEY] 与 [PluginApi.ADK_VERSION_META_DATA_KEY] 声明编译版本，
 *   缺失或与宿主不同均跳过，绝不执行插件代码。
 *
 * 加载流程：queryIntentServices 发现 → APK 元数据兼容性校验 → DexClassLoader
 * （parent=宿主 classLoader，保证 ADK Plugin / AgentPlugin 类身份与宿主一致，避免
 * ClassCastException）→ 反射实例化 → apiVersion 校验 → 初始化。
 *
 * 单个插件失败仅跳过、不影响其它插件。仅支持纯 Kotlin 插件（无 native lib）。
 */
class InstalledApkPluginLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configStore: PluginConfigStore,
    storageRegistry: StorageRegistry,
    private val notices: PluginLoadNoticeQueue,
) : PluginLoader {

    private val optimizedRoot = storageRegistry.resolve(PluginStorage.OPTIMIZED_ROOT_ID, create = true)

    /**
     * 加载并缓存结果。缓存为「包名 → 插件」映射，[refresh] 只增删不改实例：
     * 已加载插件保持同一实例（避免重复实例化与类加载器冲突）。
     */
    override fun load(): List<LoadedPlugin> = synchronized(lock) {
        if (cache == null) {
            cache = discover().associateBy { it.packageName }
        }
        cache.orEmpty().values.toList()
    }

    /**
     * 重新发现已安装插件 APK。已加载且未更新的包保留原实例（避免重复实例化与类加载器冲突）；
     * 新增的包与**安装更新时间变化**（同包名升级）的包重新实例化，已卸载的包从缓存移除。
     *
     * @return 本次新增或更新的插件。
     */
    override fun refresh(): List<LoadedPlugin> = synchronized(lock) {
        val discovered = discover()
        val current = cache.orEmpty()
        val fresh = discovered.filter { candidate ->
            val old = current[candidate.packageName]
            old == null || candidate.lastUpdateTime != old.lastUpdateTime
        }
        cache = discovered.associateBy { it.packageName }
        fresh
    }

    /** 全量发现并实例化（不读缓存）。 */
    private fun discover(): List<LoadedPlugin> {
        val services = runCatching {
            context.packageManager.queryIntentServices(
                Intent(PluginApi.DISCOVERY_ACTION),
                PackageManager.ResolveInfoFlags.of(0L),
            )
        }.getOrElse { error ->
            Log.w(TAG, "Plugin discovery failed", error)
            return emptyList()
        }
        Log.d(TAG, "Discovered ${services.size} plugin package(s)")
        return services.mapNotNull { resolveInfo ->
            loadPlugin(resolveInfo.serviceInfo?.packageName)
        }
    }

    private val lock = Any()
    private var cache: Map<String, LoadedPlugin>? = null

    /** 插件 APK 的安装更新时间；拿不到时回退 0（等同不检测更新）。 */
    private fun lastUpdateTime(packageName: String): Long = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime
    }.getOrDefault(0L)

    private fun loadPlugin(packageName: String?): LoadedPlugin? {
        if (packageName == null) return null
        var displayName = packageName
        val updateTime = lastUpdateTime(packageName)
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA,
            )
            displayName = context.packageManager.getApplicationLabel(appInfo).toString()
            val metadata = appInfo.metaData
            // 不能先实例化再检查：旧 ADK 的构造器/静态初始化可能已触发链接错误。
            if (!PluginCompat.isCompatible(
                    metadata?.getInt(PluginApi.API_VERSION_META_DATA_KEY),
                    metadata?.getString(PluginApi.ADK_VERSION_META_DATA_KEY),
                )
            ) {
                Log.w(TAG, "Incompatible plugin '$packageName'; skipping before class loading")
                notices.report(packageName, updateTime, displayName)
                return null
            }
            val className = appInfo.metaData?.getString(PluginApi.CLASS_META_DATA_KEY)
                ?: error("Missing ${PluginApi.CLASS_META_DATA_KEY} in $packageName")
            val sourceDir = appInfo.sourceDir
                ?: error("Missing sourceDir for $packageName")

            val optimizedDir = pluginOptimizedDirectory(optimizedRoot, packageName).apply { mkdirs() }
            val dexClassLoader = DexClassLoader(
                sourceDir,
                optimizedDir.absolutePath,
                null,
                context.classLoader,
            )
            val pluginClass = Class.forName(className, false, dexClassLoader)
            val plugin = pluginClass.getDeclaredConstructor().newInstance() as AgentPlugin

            if (!PluginCompat.isCompatible(plugin.apiVersion)) {
                notices.report(packageName, updateTime, displayName)
                return null
            }

            // 通过静态声明和实例协议检查后才允许插件初始化与配置回填。
            plugin.onAttach(context.applicationContext)
            plugin.configure(configStore.valuesFor(plugin.pluginId))

            Log.i(TAG, "Loaded plugin '${plugin.pluginId}' from $packageName")
            LoadedPlugin(packageName, plugin, lastUpdateTime = updateTime)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: LinkageError) {
            // 声明错误或初始化过程中才解析的 ADK 符号也必须隔离，不能使宿主闪退。
            Log.w(TAG, "Incompatible plugin '$packageName'", error)
            notices.report(packageName, updateTime, displayName)
            null
        } catch (error: Exception) {
            val cause = if (error is InvocationTargetException) error.targetException else error
            if (cause is CancellationException) throw cause
            if (cause is Error && cause !is LinkageError) throw cause
            if (cause is LinkageError) notices.report(packageName, updateTime, displayName)
            Log.w(TAG, "Failed to load plugin '$packageName'", error)
            null
        }
    }

    private companion object {
        const val TAG: String = "InstalledApkPluginLoader"
    }
}
