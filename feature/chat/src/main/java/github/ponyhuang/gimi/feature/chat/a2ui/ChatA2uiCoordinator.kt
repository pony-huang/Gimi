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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** ViewModel 持有的渲染运行时；按会话隔离 Snapshot 状态，不在 Composable 中启动业务流。 */
internal class ChatA2uiCoordinator(
    private val scope: CoroutineScope,
    private val protocol: A2uiProtocol,
    private val canSubmit: (String) -> Boolean,
    private val submit: (String, A2uiInteraction) -> Unit,
    openUrl: (String) -> Unit,
    private val onChanged: (String, Map<String, A2uiSurfaceModel>, Map<String, String>) -> Unit,
) {
    private val basicCatalog = chatA2uiCatalog(openUrl)
    private val catalog = extendedChatA2uiCatalog(basicCatalog)
    private val renderCatalogs = listOf(basicCatalog, catalog)
    private val renderCatalogIds = renderCatalogs.map { it.id }.toSet()
    val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
    private val sessions = mutableMapOf<String, Session>()

    private inner class Session(val id: String) {
        val processor = A2uiMessageProcessor(renderCatalogs, listOf(A2uiActionInterceptor { action ->
            action.takeIf { canSubmit(id) }
        }))
        val stagedUpdates = mutableMapOf<String, Job>()
        val liveStarts = mutableMapOf<String, TimeSource.Monotonic.ValueTimeMark>()
        val documents = mutableMapOf<String, A2uiDocument>()
        var owners: Map<String, String> = emptyMap()
        var surfaces: Map<String, A2uiSurfaceModel> = emptyMap()

        /** 上一次完成重放的记录；流式发布高频进入 sync，用它跳过未变化历史的全量 JSON 解析。 */
        var syncedRecords: List<A2uiRecord>? = null
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

        fun close() {
            jobs.forEach { it.cancel() }
            stagedUpdates.values.forEach { it.cancel() }
        }
    }

    fun release(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    fun sync(sessionId: String, messages: List<Message>, progressive: Boolean = false) {
        val records = messages.flatMap { message -> buildList {
            message.a2uiInteraction?.let { add(A2uiRecord.Submission(it)) }
            message.functionResponses.mapNotNull { it.a2uiDocument }.forEach { add(A2uiRecord.Revision(it)) }
        } }
        if (records.isEmpty() && sessionId !in sessions) return
        val session = sessions.getOrPut(sessionId) { Session(sessionId) }
        if (records == session.syncedRecords) {
            // 记录未变化时跳过重放，但仍要重发当前 surfaces：切换会话回来时
            // showRuntime 已把 uiState 的 a2uiSurfaces/a2uiOwners 清空，靠这里恢复。
            onChanged(sessionId, session.surfaces, session.owners)
            return
        }
        session.syncedRecords = records
        // 历史标准卡片按原目录渲染，新生成的卡片使用扩展目录。
        val active = protocol.replay(records).filter { document ->
            val create = document.messages.firstOrNull() ?: return@filter false
            val surface = (Json.parseToJsonElement(create) as? JsonObject)
                ?.get("createSurface") as? JsonObject
            (surface?.get("catalogId") as? JsonPrimitive)?.contentOrNull in renderCatalogIds
        }.associateBy { it.surfaceId }
        session.owners = buildMap {
            messages.forEach { message -> message.functionResponses.forEach { response ->
                response.a2uiDocument?.takeIf { it.surfaceId in active }?.let { putIfAbsent(it.surfaceId, message.id) }
            } }
        }
        val parser = A2uiMessageParser()
        (session.documents.keys - active.keys).forEach { id ->
            session.stagedUpdates.remove(id)?.cancel()
            session.liveStarts.remove(id)
            session.processor.processInput(parser, deleteMessage(id))
            session.documents.remove(id)
        }
        active.forEach { (id, document) ->
            val previous = session.documents[id]
            if (previous == document) return@forEach
            val wasStaged = session.stagedUpdates.remove(id)?.also { it.cancel() } != null
            session.documents[id] = document

            fun applyRevision() {
                // 仍在显示的组件需要保留绑定；可兼容的修订直接更新原 surface。
                val dataOnly = previous != null && previous.messages.size == 3 && document.messages.size == 3 &&
                    previous.messages[0] == document.messages[0] && previous.messages[2] == document.messages[2]
                val appendOnly = previous != null && canExtendSurface(previous, document)
                if (dataOnly || appendOnly) {
                    if (previous.messages[1] != document.messages[1]) {
                        session.processor.processInput(parser, document.messages[1])
                    }
                    if (wasStaged || !dataOnly) session.processor.processInput(parser, document.messages[2])
                } else {
                    if (previous != null) session.processor.processInput(parser, deleteMessage(id))
                    document.messages.forEach { session.processor.processInput(parser, it) }
                }
            }

            if (previous == null && progressive) {
                session.liveStarts[id] = TimeSource.Monotonic.markNow()
                val structure = a2uiStructureMessage(document)
                if (structure != null) {
                    document.messages.take(2).forEach { session.processor.processInput(parser, it) }
                    session.processor.processInput(parser, structure)
                    session.stagedUpdates[id] = scope.launch {
                        delay(A2uiStructureDisplayTime)
                        session.processor.processInput(parser, document.messages[2])
                        session.stagedUpdates.remove(id)
                    }
                    return@forEach
                }
            }
            val remaining = (if (previous == null) null else session.liveStarts.remove(id))?.let { A2uiStructureDisplayTime - it.elapsedNow() } ?: ZERO
            if (previous != null && remaining > ZERO && previous.messages.getOrNull(2) != document.messages.getOrNull(2)) {
                session.stagedUpdates[id] = scope.launch {
                    delay(remaining)
                    applyRevision()
                    session.stagedUpdates.remove(id)
                }
            } else {
                applyRevision()
            }
        }
        onChanged(sessionId, session.surfaces, session.owners)
    }

    /** 保留已显示节点的类型和 id，官方处理器可在原 surface 上补齐剩余组件。 */
    private fun canExtendSurface(previous: A2uiDocument, next: A2uiDocument): Boolean {
        if (previous.messages.size != 3 || next.messages.size != 3 || previous.messages[0] != next.messages[0]) return false
        fun types(document: A2uiDocument) =
            Json.parseToJsonElement(document.messages[2]).jsonObject.getValue("updateComponents").jsonObject
                .getValue("components").jsonArray.associate {
                    val component = it.jsonObject
                    component.getValue("id").jsonPrimitive.content to component.getValue("component").jsonPrimitive.content
                }
        val oldTypes = types(previous)
        val newTypes = types(next)
        return oldTypes.all { (id, type) -> newTypes[id] == type }
    }

    private fun deleteMessage(id: String) = buildJsonObject {
        put("version", "v0.9.1")
        put("deleteSurface", buildJsonObject { put("surfaceId", id) })
    }.toString()
}

internal val A2uiStructureDisplayTime = 240.milliseconds

/** 使用已校验文档中的原始节点；AndroidX 会为暂未收到的子节点显示自己的加载态。 */
internal fun a2uiStructureMessage(document: A2uiDocument): String? {
    val message = document.messages.getOrNull(2)?.let { Json.parseToJsonElement(it).jsonObject } ?: return null
    val update = message.getValue("updateComponents").jsonObject
    val components = update.getValue("components").jsonArray
    val root = components.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == "root" }?.jsonObject ?: return null
    val rootType = root.getValue("component").jsonPrimitive.content
    val structure = when (rootType) {
        "Column", "Row", "List" -> listOf(root)
        "Card" -> {
            val childId = root["child"]?.jsonPrimitive?.content
            val child = components.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == childId }
            listOfNotNull(root, child)
        }
        else -> return null
    }
    if (structure.size >= components.size) return null
    return JsonObject(message + ("updateComponents" to JsonObject(update + ("components" to JsonArray(structure))))).toString()
}

private fun jsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to jsonValue(it.value) })
    is List<*> -> JsonArray(value.map(::jsonValue))
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}
