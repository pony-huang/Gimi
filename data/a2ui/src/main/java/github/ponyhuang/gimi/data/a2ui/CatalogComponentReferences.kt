package github.ponyhuang.gimi.data.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 从实际目录的 ComponentId/ChildList 类型取出图边；不把业务正文或事件上下文误当成组件 ID。 */
internal class CatalogComponentReferences(private val catalog: JsonObject) {
    fun of(type: String, properties: Map<String, Any?>): Set<String> = buildSet {
        val schema = (catalog["components"] as? JsonObject)?.get(type) ?: return@buildSet
        visit(schema, properties, emptySet(), this)
    }

    private fun visit(schema: JsonElement, value: Any?, resolving: Set<String>, result: MutableSet<String>) {
        val node = schema as? JsonObject ?: return
        val ref = (node["\$ref"] as? JsonPrimitive)?.contentOrNull
        if (ref != null) {
            when {
                ref.substringBefore('#').endsWith("common_types.json") -> {
                    when (ref.substringAfter("#/\$defs/", "")) {
                        "ComponentId" -> (value as? String)?.let(result::add)
                        "ChildList" -> when (value) {
                            is List<*> -> value.filterIsInstance<String>().forEach(result::add)
                            is Map<*, *> -> (value["componentId"] as? String)?.let(result::add)
                            else -> Unit
                        }
                        else -> Unit
                    }
                }
                ref !in resolving -> {
                    val path = ref.substringAfter('#', "")
                    require(path.startsWith('/')) { "Unsupported schema reference: $ref" }
                    val target = path.removePrefix("/").split('/').fold(catalog as JsonElement) { current, key ->
                        (current as? JsonObject)?.get(key.replace("~1", "/").replace("~0", "~"))
                            ?: throw IllegalArgumentException("Missing schema reference: $ref")
                    }
                    visit(target, value, resolving + ref, result)
                }
            }
        }
        val fields = node["properties"] as? JsonObject
        if (value is Map<*, *>) {
            fields?.forEach { (name, childSchema) ->
                if (value.containsKey(name)) visit(childSchema, value[name], resolving, result)
            }
        }
        if (value is List<*>) {
            node["items"]?.let { itemSchema -> value.forEach { visit(itemSchema, it, resolving, result) } }
        }
        listOf("allOf", "anyOf", "oneOf").forEach { key ->
            (node[key] as? JsonArray)?.forEach { visit(it, value, resolving, result) }
        }
    }
}
