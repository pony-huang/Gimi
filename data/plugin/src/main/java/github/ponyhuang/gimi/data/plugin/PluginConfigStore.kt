package github.ponyhuang.gimi.data.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件配置的宿主侧持久化（SharedPreferences，按 pluginId 存一个 JSON 对象）。
 *
 * 未来配置页通过 [save] 写入；[InstalledApkPluginLoader] 加载后经 [valuesFor]
 * 读取并调用插件 [github.ponyhuang.gimi.pluginapi.AgentPlugin.configure] 回填。
 */
@Singleton
class PluginConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun valuesFor(pluginId: String): Map<String, String> {
        val raw = prefs.getString(pluginId, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { key -> json.optString(key) }
        }.getOrDefault(emptyMap())
    }

    fun save(pluginId: String, values: Map<String, String>) {
        // 用 Map 构造器而非 json.put(key, value)：R8 在 release 会内联 forEach lambda 并把
        // JSONObject.put(String, Object) 臆造为不存在的 put(Object, String)，设备运行时抛
        // NoSuchMethodError（v2ex/spotify/zhihu 保存即闪退）。构造器签名唯一，R8 无从弄错。
        val json = JSONObject(values)
        prefs.edit().putString(pluginId, json.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME: String = "plugin_config"
    }
}
