package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.toJsonSchemaString
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import github.ponyhuang.gimi.domain.a2ui.*
import github.ponyhuang.gimi.domain.conversation.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/** ViewModel 持有的渲染运行时；按会话隔离 Snapshot 状态，不在 Composable 中启动业务流。 */
internal class ChatA2uiCoordinator(
    private val scope: CoroutineScope,
    private val protocol: A2uiProtocol,
    private val canSubmit: (String) -> Boolean,
    private val submit: (String, A2uiInteraction) -> Unit,
    openUrl: (String) -> Unit,
    private val onChanged: (String, Map<String, A2uiSurfaceModel>, Map<String, String>) -> Unit,
) {
    private val catalog = chatA2uiCatalog(openUrl)
    val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
    private val sessions = mutableMapOf<String, Session>()

    private inner class Session(val id: String) {
        val processor = A2uiMessageProcessor(listOf(catalog), listOf(A2uiActionInterceptor { action ->
            action.takeIf { canSubmit(id) }
        }))
        val documents = mutableMapOf<String, A2uiDocument>()
        var owners: Map<String, String> = emptyMap()
        var surfaces: Map<String, A2uiSurfaceModel> = emptyMap()
        private val jobs = listOf(
            scope.launch(Dispatchers.Default) { processor.collectMessages() },
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                processor.outboundEvents.collect { event ->
                    if (event is A2uiClientEventMessage && canSubmit(id)) {
                        submit(id, A2uiInteraction(
                            surfaceId = event.surfaceId,
                            componentId = event.componentId,
                            action = event.type,
                            timestamp = event.timestamp,
                            context = jsonValue(event.context).jsonObject,
                            data = jsonValue(event.clientDataModel?.surfaces.orEmpty()).jsonObject,
                        ))
                    }
                }
            },
            scope.launch {
                processor.activeSurfaces.collect { active ->
                    surfaces = active.associateBy { it.id }
                    onChanged(id, surfaces, owners)
                }
            },
        )

        fun close() = jobs.forEach { it.cancel() }
    }

    fun release(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    fun sync(sessionId: String, messages: List<Message>) {
        val records = messages.flatMap { message -> buildList {
            message.a2uiInteraction?.let { add(A2uiRecord.Submission(it)) }
            message.functionResponses.mapNotNull { it.a2uiDocument }.forEach { add(A2uiRecord.Revision(it)) }
        } }
        if (records.isEmpty() && sessionId !in sessions) return
        val session = sessions.getOrPut(sessionId) { Session(sessionId) }
        val active = protocol.replay(records).associateBy { it.surfaceId }
        session.owners = buildMap {
            messages.forEach { message -> message.functionResponses.forEach { response ->
                response.a2uiDocument?.takeIf { it.surfaceId in active }?.let { putIfAbsent(it.surfaceId, message.id) }
            } }
        }
        val parser = A2uiMessageParser()
        (session.documents.keys - active.keys).forEach { id ->
            session.processor.processInput(parser, deleteMessage(id))
            session.documents.remove(id)
        }
        active.forEach { (id, document) ->
            val previous = session.documents[id]
            if (previous != document) {
                // 提交重放只替换根数据；删除 surface 会清空仍被 Compose 读取的必填绑定，导致闪退。
                val dataOnly = previous != null && previous.messages.size == 3 && document.messages.size == 3 &&
                    previous.messages[0] == document.messages[0] && previous.messages[2] == document.messages[2]
                if (dataOnly) {
                    session.processor.processInput(parser, document.messages[1])
                } else {
                    if (previous != null) session.processor.processInput(parser, deleteMessage(id))
                    document.messages.forEach { session.processor.processInput(parser, it) }
                }
                session.documents[id] = document
            }
        }
        onChanged(sessionId, session.surfaces, session.owners)
    }

    private fun deleteMessage(id: String) = buildJsonObject {
        put("version", "v0.9.1")
        put("deleteSurface", buildJsonObject { put("surfaceId", id) })
    }.toString()
}

private fun jsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to jsonValue(it.value) })
    is List<*> -> JsonArray(value.map(::jsonValue))
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}
