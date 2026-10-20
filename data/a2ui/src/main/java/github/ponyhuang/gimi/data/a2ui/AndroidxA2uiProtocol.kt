package github.ponyhuang.gimi.data.a2ui

import androidx.a2ui.engine.platform.A2uiCoreComponentRegistry
import androidx.a2ui.engine.platform.A2uiCoreDataModel
import androidx.a2ui.engine.processor.A2uiCoreMessageProcessor
import androidx.a2ui.model.processor.A2uiJsonMessageParser
import androidx.a2ui.model.protocol.*
import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import github.ponyhuang.gimi.domain.a2ui.A2uiProtocol
import github.ponyhuang.gimi.domain.a2ui.A2uiRecord
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject

/** 用独立的官方引擎校验候选修订；成功前不修改宿主正在显示的 surface。 */
class AndroidxA2uiProtocol @Inject constructor() : A2uiProtocol {
    override suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities) {
        require(document.surfaceId.isNotBlank()) { "Missing surfaceId" }
        val parser = A2uiJsonMessageParser(::ProtocolJsonReader)
        val messages = try {
            document.messages.map(parser::parse)
        } catch (failure: A2uiException) {
            throw IllegalArgumentException(failure.message, failure)
        }
        require(messages.all { it.surfaceId == document.surfaceId }) { "Surface IDs must match" }
        if (document.deleted) {
            require(messages.size == 1 && messages.single() is A2uiDeleteSurfaceMessage)
            return
        }
        require(messages.size == 3 && messages[0] is A2uiCreateSurfaceMessage &&
            messages[1] is A2uiUpdateDataModelMessage && messages[2] is A2uiUpdateComponentsMessage) {
            "A revision must contain createSurface, updateDataModel, updateComponents in that order"
        }
        require((messages[0] as A2uiCreateSurfaceMessage).catalogId == capabilities.catalogId) { "Unsupported catalog" }
        require((messages[1] as A2uiUpdateDataModelMessage).path == "/") { "Expected complete root data" }
        val components = (messages[2] as A2uiUpdateComponentsMessage).components
        require(components.isNotEmpty() && components.size <= 256) { "Expected 1..256 components" }
        val ids = components.map { it.id }.toSet()
        require(ids.size == components.size && "root" in ids) { "Unique component IDs and root required" }
        val edges = components.associate { component ->
            component.id to buildList {
                (component.properties["child"] as? String)?.let(::add)
                (component.properties["children"] as? List<*>)?.filterIsInstance<String>()?.let(::addAll)
                (component.properties["children"] as? Map<*, *>)?.get("componentId")?.let { add(it as String) }
            }
        }
        val visited = mutableSetOf<String>()
        fun visit(id: String, stack: Set<String>) {
            require(id in ids && id !in stack) { "Missing or cyclic component: $id" }
            if (visited.add(id)) edges.getValue(id).forEach { visit(it, stack + id) }
        }
        ids.forEach { visit(it, emptySet()) }
        val catalog = CatalogSchema(capabilities.catalogId, Json.parseToJsonElement(capabilities.schemaJson).jsonObject)
        coroutineScope {
            val errors = mutableListOf<String>()
            val complete = CompletableDeferred<List<String>>()
            val processor = A2uiCoreMessageProcessor(
                catalogs = listOf(catalog),
                dataModelFactory = { ValidationDataModel() },
                componentRegistryFactory = {
                    object : A2uiCoreComponentRegistry {
                        override fun reportError(id: String, exception: A2uiException) { errors += "$id: ${exception.message}" }
                        override fun update(components: List<A2uiComponentPayload>) { complete.complete(errors.toList()) }
                        override fun close() = Unit
                    }
                },
            )
            val job = launch { processor.collectMessages() }
            try {
                messages.forEach(processor::processMessage)
                val failures = withTimeout(5_000) { complete.await() }
                require(failures.isEmpty()) { failures.joinToString("; ") }
            } finally {
                job.cancel()
            }
        }
    }

    override fun replay(records: List<A2uiRecord>): List<A2uiDocument> {
        val surfaces = linkedMapOf<String, A2uiDocument>()
        records.forEach { record ->
            when (record) {
                is A2uiRecord.Revision -> {
                    val document = record.document
                    if (document.deleted) surfaces.remove(document.surfaceId) else surfaces[document.surfaceId] = document
                }
                is A2uiRecord.Submission -> {
                    val interaction = record.interaction
                    val document = surfaces[interaction.surfaceId] ?: return@forEach
                    val submitted = interaction.data[interaction.surfaceId] as? JsonObject ?: return@forEach
                    // 只保存已提交的值；恢复历史不重新发送事件，也不丢失用户上次提交的表单内容。
                    surfaces[interaction.surfaceId] = document.copy(messages = document.messages.map { raw ->
                        val message = Json.parseToJsonElement(raw).jsonObject
                        val update = message["updateDataModel"] as? JsonObject
                        if (update == null) raw else JsonObject(message + ("updateDataModel" to
                            JsonObject(update + ("value" to submitted)))).toString()
                    })
                }
            }
        }
        return surfaces.values.toList()
    }
}

/** 候选修订只允许根数据替换；校验阶段无需绑定观察或执行客户端函数。 */
private class ValidationDataModel : A2uiCoreDataModel {
    private var root: Any? = null
    override fun update(path: A2uiDataPath, value: Any?) { root = value }
    override fun get(path: A2uiDataPath): Any? = root
    override fun close() { root = null }
}
