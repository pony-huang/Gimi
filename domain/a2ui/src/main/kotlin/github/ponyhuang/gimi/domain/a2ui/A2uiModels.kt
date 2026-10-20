package github.ponyhuang.gimi.domain.a2ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 宿主实际支持的目录；schemaJson 从注册的组件导出，生成与校验共用。 */
@Serializable
data class A2uiCapabilities(val catalogId: String, val schemaJson: String)

/** 一次经过校验的完整 surface 修订；删除修订仅包含 deleteSurface 消息。 */
@Serializable
data class A2uiDocument(val surfaceId: String, val messages: List<String>, val deleted: Boolean = false)

/** 用户提交给 Agent 的结构化事件；data 为提交时的绑定数据，草稿不进入历史。 */
@Serializable
data class A2uiInteraction(
    val surfaceId: String,
    val componentId: String,
    val action: String,
    val timestamp: Long,
    val context: JsonObject,
    val data: JsonObject,
)

/** 宿主按持久化顺序交付的 UI 历史：既包括 Agent 修订，也包括已提交的用户数据。 */
sealed interface A2uiRecord {
    /** Agent 对单个 surface 的完整修订。 */
    data class Revision(val document: A2uiDocument) : A2uiRecord
    /** 用户提交的绑定数据，重放时覆盖此前修订的数据模型。 */
    data class Submission(val interaction: A2uiInteraction) : A2uiRecord
}

/** 宿主无关的 surface 校验及事件重放；无效输入抛出 IllegalArgumentException。 */
interface A2uiProtocol {
    suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities)
    fun replay(records: List<A2uiRecord>): List<A2uiDocument>
}

/** 结构化用户事件在文本型模型输入中的唯一信封，避免散落的字符串拼接规则。 */
object A2uiInteractionCodec {
    fun encode(interaction: A2uiInteraction): String = buildJsonObject {
        put("a2ui_interaction", Json.encodeToJsonElement(interaction))
    }.toString()

    fun decode(text: String): A2uiInteraction? = try {
        val payload = Json.parseToJsonElement(text).jsonObject
        if (payload.keys != setOf("a2ui_interaction")) null
        else Json.decodeFromJsonElement(A2uiInteraction.serializer(), payload.getValue("a2ui_interaction"))
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** ADK 等宿主的工具结果信封；不猜测普通文本中的 JSON。 */
object A2uiToolResult {
    const val TOOL_NAME = "generate_a2ui"
    const val DOCUMENT_KEY = "a2ui_document"

    fun encode(document: A2uiDocument): String = Json.encodeToString(document)

    fun extract(toolName: String, response: Map<String, Any?>): A2uiDocument? {
        if (toolName != TOOL_NAME) return null
        val payload = (response["result"] as? Map<*, *>) ?: response
        val raw = payload[DOCUMENT_KEY] as? String ?: return null
        return try {
            Json.decodeFromString<A2uiDocument>(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
