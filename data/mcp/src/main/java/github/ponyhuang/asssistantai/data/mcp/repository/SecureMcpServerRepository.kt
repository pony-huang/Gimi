package github.ponyhuang.asssistantai.data.mcp.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import github.ponyhuang.asssistantai.domain.mcp.model.McpImportResult
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.mcp.model.McpTransport
import github.ponyhuang.asssistantai.domain.mcp.repository.McpRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SecureMcpServerRepository @Inject constructor(
    private val storage: McpServerStorage,
) : McpRepository {
    private val gson = Gson()
    private val type = object : TypeToken<List<McpServer>>() {}.type
    private val servers = MutableStateFlow(load())
    private val _revision = MutableStateFlow(0L)

    override val revision = _revision.asStateFlow()

    override fun observeServers() = servers.asStateFlow()

    override fun currentServers(): List<McpServer> = servers.value

    override fun server(id: String): McpServer? = servers.value.firstOrNull { it.id == id }

    override fun save(server: McpServer) {
        val updated = servers.value.toMutableList().apply {
            val index = indexOfFirst { it.id == server.id }
            if (index < 0) add(server) else set(index, server)
        }
        persist(updated)
    }

    override fun delete(id: String) = persist(servers.value.filterNot { it.id == id })

    override fun importJson(json: String): McpImportResult {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { return McpImportResult(error = "JSON 格式无效") }
        val source = root.getAsJsonObject("mcpServers") ?: root
        val imported = mutableListOf<McpServer>()
        var skipped = 0
        source.entrySet().forEach { (name, element) ->
            val config = element.takeIf { it.isJsonObject }?.asJsonObject
                ?: run { skipped++; return@forEach }
            val transport = when (config.string("type")?.lowercase()) {
                "http", "streamablehttp", "streamable_http" -> McpTransport.STREAMABLE_HTTP
                "sse" -> McpTransport.SSE
                else -> null
            }
            val url = config.string("url")
            if (transport == null || url.isNullOrBlank()) {
                skipped++
                return@forEach
            }
            imported += McpServer(
                name = name,
                endpointUrl = url,
                transport = transport,
                headers = config.getAsJsonObject("headers")?.toHeaderLines().orEmpty(),
            )
        }
        if (imported.isNotEmpty()) persist(servers.value + imported)
        return McpImportResult(imported = imported.size, skipped = skipped)
    }

    private fun load(): List<McpServer> = runCatching {
        gson.fromJson<List<McpServer>>(storage.read() ?: "[]", type)
    }.getOrDefault(emptyList())

    private fun persist(value: List<McpServer>) {
        if (value == servers.value) return
        storage.write(gson.toJson(value, type))
        servers.value = value
        _revision.value += 1
    }
}

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.toHeaderLines(): String = entrySet().joinToString("\n") { (name, value) ->
    "$name=${value.asString}"
}
