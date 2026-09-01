package github.ponyhuang.gimi.data.memory

import github.ponyhuang.gimi.domain.memory.model.ManagedMemory
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryFeedback
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryPage
import github.ponyhuang.gimi.domain.memory.repository.Mem0MemoryManagementRepository
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 通过 Mem0 REST API 读取、删除及反馈当前应用用户的云端记忆。 */
class Mem0MemoryManagementRepositoryImpl(
    private val api: Mem0ApiClient,
) : Mem0MemoryManagementRepository {

    override suspend fun loadPage(page: Int, pageSize: Int): ManagedMemoryPage {
        require(page >= 1) { "page must be positive." }
        require(pageSize in 1..200) { "pageSize must be between 1 and 200." }
        val response = api.post(
            path = "/v3/memories/?page=$page&page_size=$pageSize",
            body = buildJsonObject {
                put("filters", buildJsonObject {
                    put("AND", JsonArray(listOf(
                        buildJsonObject { put("user_id", DEFAULT_USER_ID) },
                        buildJsonObject { put("app_id", APP_NAME) },
                    )))
                })
            },
        )
        return ManagedMemoryPage(
            memories = (response["results"] as? JsonArray).orEmpty().mapNotNull(::toManagedMemory),
            hasNextPage = response["next"]?.jsonPrimitive?.contentOrNull != null,
        )
    }

    override suspend fun delete(memoryId: String) {
        require(memoryId.isNotBlank()) { "memoryId must not be blank." }
        api.delete("/v1/memories/$memoryId/")
    }

    override suspend fun submitFeedback(memoryId: String, feedback: ManagedMemoryFeedback, reason: String?) {
        require(memoryId.isNotBlank()) { "memoryId must not be blank." }
        api.post(
            path = "/v1/feedback/",
            body = buildJsonObject {
                put("memory_id", memoryId)
                put("feedback", feedback.name)
                reason?.trim()?.takeIf(String::isNotEmpty)?.let { put("feedback_reason", it) }
            },
        )
    }

    private fun toManagedMemory(element: JsonElement): ManagedMemory? {
        val value = element.jsonObject
        val id = value["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
        val text = value["memory"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
        return ManagedMemory(
            id = id,
            text = text,
            createdAt = value["created_at"]?.jsonPrimitive?.contentOrNull?.toInstantOrNull(),
            updatedAt = value["updated_at"]?.jsonPrimitive?.contentOrNull?.toInstantOrNull(),
        )
    }

    private fun String.toInstantOrNull(): Instant? = runCatching(Instant::parse).getOrNull()

    private companion object {
        const val APP_NAME = "Gimi"
        const val DEFAULT_USER_ID = "user-default"
    }
}
