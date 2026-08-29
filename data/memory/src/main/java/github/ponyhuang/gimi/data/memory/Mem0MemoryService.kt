package github.ponyhuang.gimi.data.memory

import com.google.adk.kt.events.Event
import com.google.adk.kt.memory.MemoryEntry
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.memory.SearchMemoryResponse
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.repository.MemoryRuntimeStatus
import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class Mem0MemoryService(
    private val httpClient: OkHttpClient,
    private val settingsRepository: MemorySettingsRepository,
    private val runtimeStatus: MemoryRuntimeStatus,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : MemoryService {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun searchMemory(
        appName: String,
        userId: String,
        query: String,
    ): SearchMemoryResponse {
        if (query.isBlank()) return SearchMemoryResponse(emptyList())
        return recover(MemoryOperation.SEARCH, SearchMemoryResponse(emptyList())) {
            val requestBody = buildJsonObject {
                put("query", query)
                put("filters", buildJsonObject {
                    put("AND", buildJsonArray {
                        add(buildJsonObject { put("user_id", userId) })
                        add(buildJsonObject { put("app_id", appName) })
                    })
                })
                put("top_k", SEARCH_LIMIT)
            }
            val response = execute("/v3/memories/search/", requestBody)
            SearchMemoryResponse(
                (response["results"] as? JsonArray).orEmpty().mapNotNull(::toMemoryEntry),
            )
        }
    }

    override suspend fun addSessionToMemory(session: Session) {
        val messages = session.events.mapNotNull(::toMessage)
        if (messages.isEmpty()) return
        recover(MemoryOperation.WRITE, Unit) {
            execute(
                path = "/v3/memories/add/",
                body = buildJsonObject {
                    put("messages", buildJsonArray { messages.forEach(::add) })
                    put("user_id", session.key.userId)
                    put("app_id", session.key.appName)
                },
            )
            Unit
        }
    }

    override suspend fun addEventsToMemory(
        appName: String,
        userId: String,
        events: List<Event>,
        sessionId: String?,
        customMetadata: Map<String, Any?>?,
    ) {
        val messages = events.mapNotNull(::toMessage)
        if (messages.isEmpty()) return
        recover(MemoryOperation.WRITE, Unit) {
            execute(
                path = "/v3/memories/add/",
                body = buildJsonObject {
                    put("messages", buildJsonArray { messages.forEach(::add) })
                    put("user_id", userId)
                    put("app_id", appName)
                },
            )
            Unit
        }
    }

    private suspend fun execute(path: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.configuration.value.apiKey
        require(apiKey.isNotBlank()) { "Mem0 API key is missing." }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Token $apiKey")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Mem0 HTTP ${response.code}: ${responseBody.take(200)}")
            if (responseBody.isBlank()) JsonObject(emptyMap())
            else json.parseToJsonElement(responseBody).jsonObject
        }
    }

    /**
     * 网络/响应解析失败时降级（fail-open），但配置错误（如 [require] 抛出的
     * [IllegalStateException]）与真实 bug 继续上抛，避免掩盖问题；只记录可恢复类型。
     *
     * 搜索失败静默返回空结果；写入失败先上报再继续抛出，让调用方（增量写入插件）
     * 知道本轮未入库、可留待下次重试，而不是把失败事件标记为已写入。
     */
    private suspend fun <T> recover(operation: MemoryOperation, fallback: T, block: suspend () -> T): T =
        try {
            block().also { runtimeStatus.reportSuccess(operation) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            logger.warning("Mem0 ${operation.name.lowercase()} response unparseable: ${failure.message}")
            runtimeStatus.reportFailure(operation)
            if (operation == MemoryOperation.WRITE) throw failure
            fallback
        } catch (failure: IOException) {
            logger.warning("Mem0 ${operation.name.lowercase()} failed: ${failure.message}")
            runtimeStatus.reportFailure(operation)
            if (operation == MemoryOperation.WRITE) throw failure
            fallback
        }

    private fun toMemoryEntry(element: JsonElement): MemoryEntry? {
        val value = element.jsonObject
        val text = value["memory"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
        val metadata = value["metadata"] as? JsonObject ?: JsonObject(emptyMap())
        return MemoryEntry(
            content = Content(parts = listOf(Part(text = text))),
            id = value["id"]?.jsonPrimitive?.contentOrNull,
            author = metadata["author"]?.jsonPrimitive?.contentOrNull ?: "user",
            timestamp = value["created_at"]?.jsonPrimitive?.contentOrNull
                ?: value["updated_at"]?.jsonPrimitive?.contentOrNull,
            customMetadata = metadata.mapValues { (_, item) -> item.toNativeValue() },
        )
    }

    private fun toMessage(event: Event): JsonObject? {
        val content = event.content ?: return null
        val role = when (content.role) {
            Role.USER -> "user"
            Role.MODEL -> "assistant"
            else -> return null
        }
        val text = content.parts.mapNotNull(Part::text).filter(String::isNotBlank).joinToString(" ")
        if (text.isBlank()) return null
        return buildJsonObject {
            put("role", role)
            put("content", text)
        }
    }

    private fun JsonElement.toNativeValue(): Any = when (this) {
        JsonNull -> ""
        is JsonObject -> mapValues { (_, value) -> value.toNativeValue() }
        is JsonArray -> map { item -> item.toNativeValue() }
        // 引号字符串保持原样，避免 "00123" 被 intOrNull 解析成 123（丢前导零）、"false" 被当成布尔。
        is JsonPrimitive -> if (isString) content else booleanOrNull ?: intOrNull ?: doubleOrNull ?: content
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.mem0.ai"
        const val SEARCH_LIMIT = 5
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val logger: Logger = Logger.getLogger(Mem0MemoryService::class.java.name)
    }
}
