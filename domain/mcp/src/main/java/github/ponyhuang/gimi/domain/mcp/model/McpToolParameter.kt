package github.ponyhuang.gimi.domain.mcp.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 工具详情中一项入参的声明，不包含用户实际调用时填写的值。
 * @property type JSON Schema 类型表达式；未声明或无法解析时为空。
 * @property required 该名称是否列入根 schema 的 required。
 * @property defaultValue 服务端声明的默认值，保留 JSON 格式。
 * @property enumValues 服务端声明的允许值，保留 JSON 格式。
 */
data class McpToolParameter(
    val name: String,
    val type: String?,
    val description: String,
    val required: Boolean,
    val defaultValue: String? = null,
    val enumValues: List<String> = emptyList(),
)

/** 从服务器原始定义读取参数，缺失字段不编造类型或默认值。 */
fun McpToolSummary.parameters(): List<McpToolParameter> {
    val root = inputSchema ?: return emptyList()
    val required = (root["required"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    return (root["properties"] as? JsonObject).orEmpty().map { (name, value) ->
        val schema = value as? JsonObject
        McpToolParameter(
            name = name,
            type = schema?.let { parameterType(it, root, 0) },
            description = (schema?.get("description") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            required = name in required,
            defaultValue = schema?.get("default")?.toString(),
            enumValues = (schema?.get("enum") as? JsonArray).orEmpty().map(JsonElement::toString),
        )
    }
}

private fun parameterType(schema: JsonObject, root: JsonObject, depth: Int): String? {
    // 自引用和深层数组不能让详情展示无限递归；原始定义仍保留在工具模型中。
    if (depth >= 12) return null
    val type = schema["type"]
    if (type is JsonArray) return type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.distinct().joinToString(" | ").ifBlank { null }
    if (type is JsonPrimitive) {
        if (type.content != "array") return type.content
        val itemType = (schema["items"] as? JsonObject)?.let { parameterType(it, root, depth + 1) }
        return if (itemType == null) "array" else "array<$itemType>"
    }
    for (keyword in listOf("anyOf", "oneOf")) {
        val variants = schema[keyword] as? JsonArray ?: continue
        val types = variants.mapNotNull { (it as? JsonObject)?.let { member -> parameterType(member, root, depth + 1) } }.distinct()
        if (types.isNotEmpty()) return types.joinToString(" | ")
    }
    val reference = (schema["\$ref"] as? JsonPrimitive)?.contentOrNull
    if (reference?.startsWith("#/") == true) {
        var target: JsonElement? = root
        reference.removePrefix("#/").split('/').forEach { key ->
            target = (target as? JsonObject)?.get(key.replace("~1", "/").replace("~0", "~"))
        }
        return (target as? JsonObject)?.let { parameterType(it, root, depth + 1) }
    }
    return when {
        schema["properties"] is JsonObject -> "object"
        schema["items"] != null -> "array"
        else -> null
    }
}
