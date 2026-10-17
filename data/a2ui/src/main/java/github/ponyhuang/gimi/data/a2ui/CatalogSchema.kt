package github.ponyhuang.gimi.data.a2ui

import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.engine.catalog.A2uiCoreComponentDefinition
import androidx.a2ui.engine.catalog.A2uiCoreComponentDefinitionCollection
import androidx.a2ui.model.catalog.A2uiFunction
import androidx.a2ui.model.catalog.A2uiFunctionCollection
import androidx.a2ui.model.catalog.A2uiFunctionDefinition
import androidx.a2ui.model.catalog.A2uiFunctionReturnType
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.schema.*
import androidx.a2ui.model.schema.commontypes.A2uiAccessibilityAttributesSchema
import androidx.a2ui.model.schema.commontypes.A2uiActionSchema
import androidx.a2ui.model.schema.commontypes.A2uiCheckRuleSchema
import androidx.a2ui.model.schema.commontypes.A2uiCheckableSchema
import androidx.a2ui.model.schema.commontypes.A2uiChildListSchema
import androidx.a2ui.model.schema.commontypes.A2uiComponentCommonSchema
import androidx.a2ui.model.schema.commontypes.A2uiComponentIdSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicBooleanSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicNumberSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringListSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicValueSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema
import kotlinx.serialization.json.*

/** 将宿主导出的目录还原为官方引擎的校验目录；不引入 Compose 或执行宿主函数。 */
internal class CatalogSchema(override val id: String, private val source: JsonObject) : A2uiCoreCatalog {
    override val title: String? = null
    override val description: String? = null
    override val themeSchema: A2uiSchema? = null
    override val isInline = false
    override val functions = A2uiFunctionCollection(
        source["functions"]?.jsonObject?.map { (name, value) ->
            val node = value.jsonObject
            val properties = node["properties"]?.jsonObject.orEmpty()
            val returnType = properties["returnType"]?.jsonObject
                ?.get("const")?.jsonPrimitive?.content
            object : A2uiFunction {
                override val definition: A2uiFunctionDefinition = object : A2uiFunctionDefinition {
                    override val name = name
                    override val description = node["description"]?.jsonPrimitive?.content.orEmpty()
                    override val argumentSchema = properties["args"]?.let(::schema) ?: A2uiAnySchema()
                    override val returnType = A2uiFunctionReturnType.entries
                        .firstOrNull { it.value == returnType } ?: A2uiFunctionReturnType.ANY
                }

                // 校验目录只描述客户端能力；真正执行由聊天宿主的 Compose 目录负责。
                override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? = null
            }
        }.orEmpty(),
    )
    override val componentDefinitions = A2uiCoreComponentDefinitionCollection(
        source.getValue("components").jsonObject.map { (name, value) ->
            object : A2uiCoreComponentDefinition {
                override val name = name
                override val description = ""
                override val propertySchema = schema(withoutDiscriminator(value))
            }
        },
    )

    private fun withoutDiscriminator(value: JsonElement): JsonElement {
        val node = value.jsonObject.toMutableMap()
        node["properties"]?.jsonObject?.let { node["properties"] = JsonObject(it - "component") }
        node["required"]?.jsonArray?.let { node["required"] = JsonArray(it.filterNot { it == JsonPrimitive("component") }) }
        listOf("allOf", "anyOf", "oneOf").forEach { key ->
            node[key]?.jsonArray?.let { node[key] = JsonArray(it.map(::withoutDiscriminator)) }
        }
        return JsonObject(node)
    }

    private fun schema(value: JsonElement): A2uiSchema {
        val node = value.jsonObject
        node["\$ref"]?.jsonPrimitive?.content?.let { ref ->
            commonTypeSchema(ref)?.let { return it }
            // 函数参数可递归引用 anyFunction；延迟解析，避免展开目录时无限递归。
            return object : A2uiCompositeSchema() {
                override val description: String? = null
                override fun getDefinition(): A2uiSchema {
                    val path = ref.substringAfter('#', "")
                    require(path.startsWith("/")) { "Unsupported schema reference: $ref" }
                    val target = path.removePrefix("/").split('/').fold(source as JsonElement) { current, key ->
                        current.jsonObject.getValue(key.replace("~1", "/").replace("~0", "~"))
                    }
                    return schema(target)
                }
            }
        }
        val keywords = buildList<A2uiSchemaKeyword<Any>> {
            node["oneOf"]?.jsonArray?.let { add(A2uiSchemaKeyword.OneOf(it.map(::schema))) }
            node["anyOf"]?.jsonArray?.let { add(A2uiSchemaKeyword.AnyOf(it.map(::schema))) }
            node["allOf"]?.jsonArray?.let { add(A2uiSchemaKeyword.AllOf(it.map(::schema))) }
            node["not"]?.let { add(A2uiSchemaKeyword.Not(schema(it))) }
            node["enum"]?.jsonArray?.let { add(A2uiSchemaKeyword.Enum(it.map { value -> plainValue(value) ?: JsonNull })) }
            node["const"]?.let { add(A2uiSchemaKeyword.Const(plainValue(it) ?: JsonNull)) }
            node["format"]?.jsonPrimitive?.content?.let { add(A2uiSchemaKeyword.Format(it)) }
            node["if"]?.let {
                add(A2uiSchemaKeyword.IfThen(schema(it), schema(node.getValue("then")), node["else"]?.let(::schema)))
            }
        }
        val typed: A2uiSchema = when (node["type"]?.jsonPrimitive?.content) {
            "object" -> A2uiObjectSchema(
                properties = node["properties"]?.jsonObject?.mapValues { schema(it.value) }.orEmpty(),
                required = node["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
                isAdditionalPropertiesAllowed = node["additionalProperties"] != JsonPrimitive(false),
                additionalPropertiesSchema = (node["additionalProperties"] as? JsonObject)?.let(::schema),
            )
            "array" -> A2uiArraySchema(
                items = node["items"]?.let(::schema),
                minItems = node["minItems"]?.jsonPrimitive?.int ?: -1,
                maxItems = node["maxItems"]?.jsonPrimitive?.int ?: -1,
            )
            "string" -> A2uiStringSchema()
            "number", "integer" -> A2uiNumberSchema()
            "boolean" -> A2uiBooleanSchema()
            else -> A2uiAnySchema()
        }
        // AllOf 将通用关键字与类型约束组合，避免不安全地转换泛型关键字列表。
        return A2uiAnySchema(keywords = listOf(A2uiSchemaKeyword.AllOf(listOf(typed))) + keywords)
    }
}

internal fun plainValue(value: JsonElement): Any? = when (value) {
    JsonNull -> null
    is JsonObject -> value.mapValues { plainValue(it.value) }
    is JsonArray -> value.map(::plainValue)
    is JsonPrimitive -> if (value.isString) value.content else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
}

/**
 * 官方目录把可复用结构放在独立的 common_types 文档中。
 * 导出目录只包含宿主组件和本地定义，因此还原校验目录时必须从官方模型取回这些定义。
 */
private fun commonTypeSchema(ref: String): A2uiSchema? {
    if (!ref.substringBefore('#').endsWith("common_types.json")) return null
    val definition = ref.substringAfter("#/\$defs/", missingDelimiterValue = "")
    return CommonTypes[definition]
        ?: throw IllegalArgumentException("Unsupported common type reference: $ref")
}

private val CommonTypes: Map<String, A2uiSchema> = mapOf(
    "AccessibilityAttributes" to A2uiAccessibilityAttributesSchema.DEFAULT_INSTANCE,
    "Action" to A2uiActionSchema.DEFAULT_INSTANCE,
    "Checkable" to A2uiCheckableSchema.DEFAULT_INSTANCE,
    "CheckRule" to A2uiCheckRuleSchema.DEFAULT_INSTANCE,
    "ChildList" to A2uiChildListSchema.DEFAULT_INSTANCE,
    "ComponentCommon" to A2uiComponentCommonSchema.DEFAULT_INSTANCE,
    "ComponentId" to A2uiComponentIdSchema.DEFAULT_INSTANCE,
    "DataBinding" to A2uiDataBindingSchema.DEFAULT_INSTANCE,
    "DynamicBoolean" to A2uiDynamicBooleanSchema.DEFAULT_INSTANCE,
    "DynamicNumber" to A2uiDynamicNumberSchema.DEFAULT_INSTANCE,
    "DynamicStringList" to A2uiDynamicStringListSchema.DEFAULT_INSTANCE,
    "DynamicString" to A2uiDynamicStringSchema.DEFAULT_INSTANCE,
    "DynamicValue" to A2uiDynamicValueSchema.DEFAULT_INSTANCE,
    "FunctionCall" to A2uiFunctionCallSchema.DEFAULT_INSTANCE,
)
