package github.ponyhuang.gimi.data.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.DexClassLoader
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginApi
import java.io.File
import javax.inject.Inject

/**
 * 基于「独立安装的插件 APK」的动态加载器（参考 keiyoushi/Tachiyomi 的 DCL 思路）。
 *
 * 发现协议：
 * - 插件 APK 声明一个 exported=true 的无功能 service，带
 *   intent-filter `action=[PluginApi.DISCOVERY_ACTION]`；
 * - `<application>` 下 `<meta-data android:name=[PluginApi.CLASS_META_DATA_KEY]>` 声明实现类全名。
 *
 * 加载流程：queryIntentServices 发现 → 取 sourceDir（插件 base.apk 路径）→ DexClassLoader
 * （parent=宿主 classLoader，保证 ADK Plugin / AgentPlugin 类身份与宿主一致，避免
 * ClassCastException）→ 反射实例化 → apiVersion 校验。
 *
 * 单个插件失败仅跳过、不影响其它插件。仅支持纯 Kotlin 插件（无 native lib）。
 */
class InstalledApkPluginLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configStore: PluginConfigStore,
) : PluginLoader {

    /**
     * 加载并缓存结果（单例由 Hilt 保证，`providePlugins` 与 `providePluginTools` 共享同一实例，
     * 避免重复反射实例化插件）。
     */
    override fun load(): List<LoadedPlugin> = loadedPlugins

    private val loadedPlugins: List<LoadedPlugin> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val services = runCatching {
            context.packageManager.queryIntentServices(
                Intent(PluginApi.DISCOVERY_ACTION),
                PackageManager.ResolveInfoFlags.of(0L),
            )
        }.getOrElse { error ->
            Log.w(TAG, "Plugin discovery failed", error)
            return@lazy emptyList()
        }
        Log.d(TAG, "Discovered ${services.size} plugin package(s)")

        services.mapNotNull { resolveInfo ->
            loadPlugin(resolveInfo.serviceInfo?.packageName)
        }
    }

    private fun loadPlugin(packageName: String?): LoadedPlugin? {
        if (packageName == null) return null
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA,
            )
            val className = appInfo.metaData?.getString(PluginApi.CLASS_META_DATA_KEY)
                ?: error("Missing ${PluginApi.CLASS_META_DATA_KEY} in $packageName")
            val sourceDir = appInfo.sourceDir
                ?: error("Missing sourceDir for $packageName")

            val optimizedDir = File(context.codeCacheDir, "plugins/$packageName").apply { mkdirs() }
            val dexClassLoader = DexClassLoader(
                sourceDir,
                optimizedDir.absolutePath,
                null,
                context.classLoader,
            )
            val pluginClass = Class.forName(className, false, dexClassLoader)
            val plugin = pluginClass.getDeclaredConstructor().newInstance() as AgentPlugin

            // 注入 applicationContext：需 Android 能力的插件（开浏览器/起本地服务/存 token）据此初始化。
            plugin.onAttach(context.applicationContext)

            // 回填宿主持久化的配置值（未来配置页写入 PluginConfigStore）。
            plugin.configure(configStore.valuesFor(plugin.pluginId))

            if (!PluginCompat.isCompatible(plugin.apiVersion)) {
                error(
                    "Plugin '$packageName' apiVersion=${plugin.apiVersion} " +
                        "is incompatible with host ${PluginApi.VERSION}; skipping."
                )
            }

            Log.i(TAG, "Loaded plugin '${plugin.pluginId}' from $packageName")
            LoadedPlugin(packageName, plugin)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to load plugin '$packageName'", error)
            null
        }
    }

    private companion object {
        const val TAG: String = "InstalledApkPluginLoader"
    }
}
