package github.ponyhuang.gimi.data.plugin

import android.content.Context
import androidx.core.content.edit
import android.util.Log
import android.util.JsonReader
import android.util.JsonWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.StringReader
import java.io.StringWriter
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
            decodeValues(raw)
        }.getOrElse { error ->
            Log.e(TAG, "Failed to read configuration for plugin '$pluginId'", error)
            emptyMap()
        }
    }

    fun save(pluginId: String, values: Map<String, String>) {
        prefs.edit { putString(pluginId, encodeValues(values)) }
    }

    /**
     * 使用平台稳定的流式 JSON API，避免 R8 将 org.json 的实现细节错误内联到应用中。
     */
    private fun decodeValues(raw: String): Map<String, String> = buildMap {
        JsonReader(StringReader(raw)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                put(reader.nextName(), reader.nextString())
            }
            reader.endObject()
        }
    }

    private fun encodeValues(values: Map<String, String>): String {
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.beginObject()
            val entries = values.entries.iterator()
            while (entries.hasNext()) {
                val entry = entries.next()
                writer.name(entry.key).value(entry.value)
            }
            writer.endObject()
        }
        return output.toString()
    }

    private companion object {
        const val TAG: String = "PluginConfigStore"
        const val PREFS_NAME: String = "plugin_config"
    }
}
