package github.ponyhuang.gimi.data.mcp.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpTransport
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    @Synchronized
    override fun save(server: McpServer) {
        val updated = servers.value.toMutableList().apply {
            val index = indexOfFirst { it.id == server.id }
            if (index < 0) add(server) else set(index, server)
        }
        persist(updated)
    }

    @Synchronized
    override fun delete(id: String) = persist(servers.value.filterNot { it.id == id })

    @Synchronized
    override fun importJson(json: String): McpImportResult {
        if (json.length > MAX_IMPORT_CHARACTERS) {
            return McpImportResult(error = "JSON 内容过大")
        }
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { return McpImportResult(error = "JSON 格式无效") }
        val wrappedSource = root.get("mcpServers")
        val source = when {
            wrappedSource == null -> root
            wrappedSource.isJsonObject -> wrappedSource.asJsonObject
            else -> return McpImportResult(error = "mcpServers 必须是对象")
        }
        val updatedServers = servers.value.toMutableList()
        val affectedServerIds = linkedSetOf<String>()
        var created = 0
        var updated = 0
        var skipped = 0
        source.entrySet().take(MAX_IMPORT_SERVERS).forEach { (name, element) ->
            val normalizedName = name.trim()
            if (normalizedName.isEmpty() || normalizedName.length > MAX_FIELD_CHARACTERS) {
                skipped++
                return@forEach
            }
            val config = element.takeIf { it.isJsonObject }?.asJsonObject
                ?: run { skipped++; return@forEach }
            val url = config.string("url")?.trim()
            if (url.isNullOrBlank() || url.length > MAX_FIELD_CHARACTERS) {
                skipped++
                return@forEach
            }
            val transport = when (config.string("type")?.lowercase()) {
                "http", "streamablehttp", "streamable_http" -> McpTransport.STREAMABLE_HTTP
                "sse" -> McpTransport.SSE
                null -> McpTransport.STREAMABLE_HTTP
                else -> null
            }
            if (transport == null) {
                skipped++
                return@forEach
            }
            val headers = config.get("headers")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.toHeaderLines()
                .orEmpty()
            val existingIndex = updatedServers.indexOfFirst { it.name.trim() == normalizedName }
            if (existingIndex < 0) {
                McpServer(
                    name = normalizedName,
                    endpointUrl = url,
                    transport = transport,
                    headers = headers,
                ).also { server ->
                    updatedServers += server
                    affectedServerIds += server.id
                    created++
                }
            } else {
                val existing = updatedServers[existingIndex]
                updatedServers[existingIndex] = existing.copy(
                    name = normalizedName,
                    endpointUrl = url,
                    transport = transport,
                    bearerToken = "",
                    headers = headers,
                )
                affectedServerIds += existing.id
                updated++
            }
        }
        skipped += (source.size() - MAX_IMPORT_SERVERS).coerceAtLeast(0)
        if (affectedServerIds.isNotEmpty()) persist(updatedServers)
        return McpImportResult(
            created = created,
            updated = updated,
            skipped = skipped,
            affectedServerIds = affectedServerIds,
        )
    }

    private fun load(): List<McpServer> = runCatching {
        gson.fromJson<List<McpServer>>(storage.read() ?: "[]", type)
    }.getOrDefault(emptyList())

    private fun persist(value: List<McpServer>) {
        if (value == servers.value) return
        storage.write(gson.toJson(value, type))
        servers.value = value
        _revision.update { it + 1 }
    }

    private companion object {
        const val MAX_IMPORT_CHARACTERS = 256 * 1024
        const val MAX_IMPORT_SERVERS = 100
        const val MAX_FIELD_CHARACTERS = 2_048
    }
}

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.toHeaderLines(): String = entrySet()
    .mapNotNull { (name, value) ->
        value.takeIf { it.isJsonPrimitive }?.asString?.let { "$name=$it" }
    }
    .joinToString("\n")
    .take(8_192)
