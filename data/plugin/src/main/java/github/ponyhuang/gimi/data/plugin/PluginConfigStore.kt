package github.ponyhuang.gimi.data.plugin

import android.content.Context
import androidx.core.content.edit
import android.util.Log
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import java.io.StringReader
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件配置的宿主侧持久化（SharedPreferences，按 pluginId 存一个 JSON 对象）。
 *
 * 插件配置常含 secret=true 的凭据字段（令牌、密钥等），因此整个 JSON 载荷经
 * [KeystoreAesGcmStringCipher]（AndroidKeyStore AES/GCM）加密后再落盘，
 * 与 McpServerStorage / NeteaseSession 等安全存储同一套载荷格式与失效自愈策略。
 *
 * 未来配置页通过 [save] 写入；[InstalledApkPluginLoader] 加载后经 [valuesFor]
 * 读取并调用插件 [github.ponyhuang.gimi.pluginapi.AgentPlugin.configure] 回填。
 */
@Singleton
class PluginConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcmStringCipher(KEY_ALIAS)

    fun valuesFor(pluginId: String): Map<String, String> {
        val payload = prefs.getString(pluginId, null) ?: return emptyMap()
        // 解密失败（密钥失效/载荷损坏）按无配置处理，密钥失效时 cipher 内部自愈。
        val raw = cipher.decryptHealing(payload) ?: return emptyMap()
        return runCatching {
            decodeValues(raw)
        }.getOrElse { error ->
            Log.e(TAG, "Failed to read configuration for plugin '$pluginId'", error)
            emptyMap()
        }
    }

    fun save(pluginId: String, values: Map<String, String>) {
        val encrypted = cipher.encryptOrThrow(encodeValues(values))
        prefs.edit { putString(pluginId, encrypted) }
    }

    /**
     * 使用平台稳定的流式 JSON API，避免 R8 将 org.json 的实现细节错误内联到应用中。
     * 持久化数据可能被外部改动或来自历史版本：非字符串值跳过该键，不让单条异常清空整个配置。
     */
    private fun decodeValues(raw: String): Map<String, String> = buildMap {
        JsonReader(StringReader(raw)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (reader.peek() == JsonToken.STRING) {
                    put(name, reader.nextString())
                } else {
                    reader.skipValue()
                }
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
        const val KEY_ALIAS: String = "gimi_plugin_config"
    }
}
