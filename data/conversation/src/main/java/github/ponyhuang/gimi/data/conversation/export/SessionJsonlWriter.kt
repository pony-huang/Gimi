package github.ponyhuang.gimi.data.conversation.export

import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.events.Event
import com.google.adk.kt.serialization.adkJson
import com.google.adk.kt.serialization.anyToJsonElement
import com.google.adk.kt.sessions.Session
import java.io.Writer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 逐事件编码为单行完整 Session，保留原始字段并避免再构造一份巨大的事件数组。 */
@OptIn(FrameworkInternalApi::class)
internal object SessionJsonlWriter {
    private val json = Json(adkJson) { prettyPrint = false }

    suspend fun write(session: Session, writer: Writer) {
        writer.write("{\"key\":")
        writer.write(buildJsonObject {
            put("appName", session.key.appName)
            put("userId", session.key.userId)
            put("id", session.key.id)
        }.toString())
        writer.write(",\"state\":")
        writer.write(json.encodeToString(JsonElement.serializer(), anyToJsonElement(session.state)))
        writer.write(",\"events\":[")
        session.events.forEachIndexed { index, event ->
            currentCoroutineContext().ensureActive()
            if (index != 0) writer.write(",")
            writer.write(json.encodeToString(Event.serializer(), event))
        }
        writer.write("],\"lastUpdateTime\":")
        writer.write(JsonPrimitive(session.lastUpdateTime.toString()).toString())
        writer.write("}\n")
    }
}
