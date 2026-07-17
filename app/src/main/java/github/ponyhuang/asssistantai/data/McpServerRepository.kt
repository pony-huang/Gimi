package github.ponyhuang.asssistantai.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class McpTransport(val label: String) {
    SSE("服务器发送事件 (SSE)"),
    STREAMABLE_HTTP("可流式传输的 HTTP"),
}

/** A remote MCP endpoint. */
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "MCP 服务器",
    val description: String = "",
    val endpointUrl: String = "",
    val transport: McpTransport = McpTransport.STREAMABLE_HTTP,
    val bearerToken: String = "",
    /** One HTTP header per line in the form `Header-Name=value`. */
    val headers: String = "",
    val isEnabled: Boolean = true,
)

@Singleton
class McpServerRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val type = object : TypeToken<List<McpServerConfig>>() {}.type
    private val _servers = MutableStateFlow(load())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    fun server(id: String): McpServerConfig? = _servers.value.firstOrNull { it.id == id }

    fun save(server: McpServerConfig) {
        val updated = _servers.value.toMutableList().apply {
            val index = indexOfFirst { it.id == server.id }
            if (index < 0) add(server) else set(index, server)
        }
        persist(updated)
    }

    fun delete(id: String) = persist(_servers.value.filterNot { it.id == id })

    /** Imports the portable `mcpServers` format used by desktop MCP clients. */
    fun importJson(json: String): McpImportResult {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { return McpImportResult(error = "JSON 格式无效") }
        val source = root.getAsJsonObject("mcpServers") ?: root
        val imported = mutableListOf<McpServerConfig>()
        var skipped = 0
        source.entrySet().forEach { (name, element) ->
            val config = element.takeIf { it.isJsonObject }?.asJsonObject ?: run { skipped++; return@forEach }
            val type = config.string("type")?.lowercase()
            val transport = when (type) {
                "http", "streamablehttp", "streamable_http" -> McpTransport.STREAMABLE_HTTP
                "sse" -> McpTransport.SSE
                else -> null
            }
            val url = config.string("url")
            if (transport == null || url.isNullOrBlank()) {
                // stdio entries contain command/args and cannot run in an Android app process.
                skipped++
                return@forEach
            }
            imported += McpServerConfig(
                name = name,
                endpointUrl = url,
                transport = transport,
                headers = config.getAsJsonObject("headers")?.toHeaderLines().orEmpty(),
            )
        }
        if (imported.isNotEmpty()) persist(_servers.value + imported)
        return McpImportResult(imported = imported.size, skipped = skipped)
    }

    private fun load(): List<McpServerConfig> = runCatching {
        Gson().fromJson<List<McpServerConfig>>(preferences.getString(SERVERS_KEY, "[]"), type)
    }.getOrDefault(emptyList())

    private fun persist(value: List<McpServerConfig>) {
        _servers.value = value
        preferences.edit { putString(SERVERS_KEY, Gson().toJson(value, type)) }
    }

    private companion object {
        const val PREFERENCES_NAME = "mcp_servers"
        const val SERVERS_KEY = "servers"
    }
}

data class McpImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
) {
    val message: String
        get() = error ?: buildString {
            append("已导入 $imported 个 MCP 服务")
            if (skipped > 0) append("；跳过 $skipped 个不受支持的 stdio 或无效配置")
        }
}

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.toHeaderLines(): String = entrySet().joinToString("\n") { (name, value) ->
    "$name=${value.asString}"
}
