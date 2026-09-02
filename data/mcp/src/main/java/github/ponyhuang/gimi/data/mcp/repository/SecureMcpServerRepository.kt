package github.ponyhuang.gimi.data.mcp.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.mcp.model.McpImportResult
import github.ponyhuang.gimi.domain.mcp.model.McpImportError
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpTransport
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

@Singleton
class SecureMcpServerRepository @Inject constructor(
    private val storage: McpServerStorage,
) : McpRepository {
    /** 兼容 Gson 旧持久化格式：字段名 = Kotlin 属性名、默认值全量写出、null 省略、忽略未知键。 */
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
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

    /** 接受设置页 JSON 或文档中可直接复制的 curl MCP 配置。 */
    override fun importConfiguration(content: String): McpImportResult {
        val portableJson = if (content.trimStart().startsWith("curl", ignoreCase = true)) {
            curlToPortableJson(content)
                ?: return McpImportResult(errorCode = McpImportError.INVALID_CURL)
        } else {
            content
        }
        return importJson(portableJson)
    }

    @Synchronized
    override fun updateAuthorization(serverId: String, authorization: String): Boolean {
        val normalized = authorization.trim()
        if (
            normalized.isBlank() ||
            normalized.length > MAX_FIELD_CHARACTERS ||
            '\r' in normalized ||
            '\n' in normalized ||
            isCredentialPlaceholder(normalized)
        ) {
            return false
        }
        val index = servers.value.indexOfFirst { it.id == serverId }
        if (index < 0) return false
        val existing = servers.value[index]
        val retainedHeaders = existing.headers.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { line ->
                line.substringBefore('=', missingDelimiterValue = "")
                    .trim()
                    .equals("Authorization", ignoreCase = true)
            }
            .toMutableList()
            .apply { add("Authorization=$normalized") }
            .joinToString("\n")
        val updated = servers.value.toMutableList().apply {
            set(index, existing.copy(bearerToken = "", headers = retainedHeaders))
        }
        persist(updated)
        return true
    }

    @Synchronized
    override fun importJson(json: String): McpImportResult {
        if (json.length > MAX_IMPORT_CHARACTERS) {
            return McpImportResult(errorCode = McpImportError.CONTENT_TOO_LARGE)
        }
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { return McpImportResult(errorCode = McpImportError.INVALID_JSON) }
        val wrappedSource = root.get("mcpServers")
        val source = when {
            wrappedSource == null -> root
            wrappedSource.isJsonObject -> wrappedSource.asJsonObject
            else -> return McpImportResult(errorCode = McpImportError.MCP_SERVERS_NOT_OBJECT)
        }
        val updatedServers = servers.value.toMutableList()
        val affectedServerIds = linkedSetOf<String>()
        val credentialRequiredServerIds = linkedSetOf<String>()
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
            val headerObject = config.get("headers")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
            // 占位认证值只建立“待补凭据”关联，绝不能作为真实 Header 落盘。
            val credentialRequired = headerObject?.requiresAuthorizationCredential() == true
            val headers = headerObject?.toHeaderLines().orEmpty()
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
                    if (credentialRequired) credentialRequiredServerIds += server.id
                    created++
                }
            } else {
                val existing = updatedServers[existingIndex]
                // 重复导入模板时保留已配置的真实认证，避免占位符反向擦除可用凭据。
                val preservedAuthorizationHeader = if (credentialRequired) {
                    existing.authorizationHeaderLine()
                } else {
                    null
                }
                val mergedHeaders = listOfNotNull(
                    headers.takeIf(String::isNotBlank),
                    preservedAuthorizationHeader,
                ).joinToString("\n")
                updatedServers[existingIndex] = existing.copy(
                    name = normalizedName,
                    endpointUrl = url,
                    transport = transport,
                    bearerToken = existing.bearerToken.takeIf { credentialRequired }.orEmpty(),
                    headers = mergedHeaders,
                )
                affectedServerIds += existing.id
                if (credentialRequired && !existing.hasAuthorizationCredential()) {
                    credentialRequiredServerIds += existing.id
                }
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
            credentialRequiredServerIds = credentialRequiredServerIds,
        )
    }

    private fun load(): List<McpServer> = runCatching {
        json.decodeFromString<List<McpServer>>(storage.read() ?: "[]")
    }.getOrDefault(emptyList())

    private fun persist(value: List<McpServer>) {
        if (value == servers.value) return
        storage.write(json.encodeToString(value))
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
        value.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeUnless(::isCredentialPlaceholder)
            ?.let { "$name=$it" }
    }
    .joinToString("\n")
    .take(8_192)

private fun JsonObject.requiresAuthorizationCredential(): Boolean = entrySet().any { (name, value) ->
    name.equals("Authorization", ignoreCase = true) &&
        value.isJsonPrimitive &&
        isCredentialPlaceholder(value.asString)
}

private fun McpServer.hasAuthorizationCredential(): Boolean =
    bearerToken.isNotBlank() || headers.lineSequence().any { line ->
        line.substringBefore('=', missingDelimiterValue = "")
            .trim()
            .equals("Authorization", ignoreCase = true) &&
            line.substringAfter('=', missingDelimiterValue = "").isNotBlank()
    }

private fun McpServer.authorizationHeaderLine(): String? = headers.lineSequence()
    .map(String::trim)
    .firstOrNull { line ->
        line.substringBefore('=', missingDelimiterValue = "")
            .trim()
            .equals("Authorization", ignoreCase = true) &&
            line.substringAfter('=', missingDelimiterValue = "").isNotBlank()
    }

private fun isCredentialPlaceholder(value: String): Boolean {
    val trimmed = value.trim()
    val normalized = if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
        trimmed.substringAfter(' ').trim()
    } else {
        trimmed
    }
    return normalized.matches(Regex("<[^>]+>")) ||
        normalized.matches(Regex("\\$\\{[^}]+}")) ||
        normalized.equals("your_access_secret", ignoreCase = true) ||
        normalized.equals("your_token", ignoreCase = true)
}

private fun curlToPortableJson(content: String): String? {
    // 只做 shell 词法拆分，不执行命令；支持文档中常见的引号和反斜杠续行格式。
    val tokens = shellTokens(content.replace(Regex("\\\\\\s*\\r?\\n"), " "))
    if (tokens.firstOrNull()?.equals("curl", ignoreCase = true) != true) return null
    val url = tokens.firstOrNull { token ->
        token.startsWith("https://", ignoreCase = true) || token.startsWith("http://", ignoreCase = true)
    } ?: return null
    val headers = linkedMapOf<String, String>()
    tokens.forEachIndexed { index, token ->
        val rawHeader = when {
            token == "-H" || token == "--header" -> tokens.getOrNull(index + 1)
            token.startsWith("--header=") -> token.substringAfter('=')
            token.startsWith("-H") && token.length > 2 -> token.substring(2)
            else -> null
        } ?: return@forEachIndexed
        val name = rawHeader.substringBefore(':', missingDelimiterValue = "").trim()
        val value = rawHeader.substringAfter(':', missingDelimiterValue = "").trim()
        if (name.isNotBlank() && value.isNotBlank()) headers[name] = value
    }
    val transport = if (
        url.trimEnd('/').endsWith("/sse", ignoreCase = true) ||
        headers.entries.any { (name, value) ->
            name.equals("Accept", ignoreCase = true) && value.equals("text/event-stream", ignoreCase = true)
        }
    ) {
        "sse"
    } else {
        "streamable_http"
    }
    val server = JsonObject().apply {
        addProperty("type", transport)
        addProperty("url", url)
        add("headers", JsonObject().apply { headers.forEach(::addProperty) })
    }
    // JsonObject.toString() 与 Gson().toJson(jsonObject) 等价；树操作为保留 Gson 依赖的唯一用途。
    return JsonObject().apply {
        add("mcpServers", JsonObject().apply { add(deriveServerName(url), server) })
    }.toString()
}

private fun shellTokens(command: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaping = false
    command.forEach { character ->
        when {
            escaping -> {
                current.append(character)
                escaping = false
            }
            character == '\\' && quote != '\'' -> escaping = true
            quote != null && character == quote -> quote = null
            quote == null && (character == '\'' || character == '"') -> quote = character
            quote == null && character.isWhitespace() -> {
                if (current.isNotEmpty()) tokens += current.toString().also { current.clear() }
            }
            else -> current.append(character)
        }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}

private fun deriveServerName(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val hostParts = uri?.host.orEmpty().split('.')
        .dropLast(1)
        .filterNot { it in setOf("www", "api", "developer", "mcp") }
        .takeLast(2)
    val pathParts = uri?.path.orEmpty().split('/')
        .filter { it.isNotBlank() }
        .filterNot { part ->
            part.lowercase() in setOf("api", "mcp", "sse") ||
                part.matches(Regex("v\\d+", RegexOption.IGNORE_CASE))
        }
        .takeLast(1)
    return (hostParts + pathParts)
        .joinToString("-")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "mcp-server" }
}
