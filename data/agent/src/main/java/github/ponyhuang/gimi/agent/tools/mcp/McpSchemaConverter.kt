package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import io.modelcontextprotocol.spec.McpSchema.JsonSchema
import io.modelcontextprotocol.spec.McpSchema.Tool

/** Converts MCP JSON Schema maps into the ADK schema model without leaking MCP SDK types. */
internal object McpSchemaConverter {

    private const val MAX_SCHEMA_DEPTH = 32
    private const val MAX_REF_EXPANSIONS = 512

    private val logger = LoggerFactory.getLogger(McpSchemaConverter::class)

    /** Tracks definitions, the active reference path, and the conversion-wide expansion budget. */
    private class RefScope(
        val definitions: Map<String, Any>,
        val visited: Set<String> = emptySet(),
        private val remaining: IntArray = intArrayOf(MAX_REF_EXPANSIONS),
    ) {
        fun spend(): Boolean {
            if (remaining[0] <= 0) return false
            remaining[0]--
            return true
        }

        fun following(ref: String): RefScope = RefScope(definitions, visited + ref, remaining)
    }

    /** Converts an MCP [Tool] into an ADK [FunctionDeclaration]. */
    fun Tool.toAdkFunctionDeclaration(): FunctionDeclaration =
        FunctionDeclaration(
            name = name(),
            description = description() ?: "",
            parameters = inputSchema().toAdkRootSchema(),
            response = outputSchema()?.let(::toResponseSchema),
        )

    /** Converts the Java MCP SDK's legacy typed schema record into the ADK schema model. */
    @Suppress("DEPRECATION")
    fun JsonSchema.toAdkSchema(): Schema {
        val definitions = buildMap {
            definitions()?.let(::putAll)
            defs()?.let(::putAll)
        }
        val properties = properties().toAdkSchemaMap(depth = 1, scope = RefScope(definitions))
        val type = parseTypeString(type())
        return Schema(
            type = type,
            properties = properties,
            items = defaultItems(type),
            required = required().requiredIn(properties),
            description = null,
        )
    }

    private fun Map<String, Any>.toAdkRootSchema(): Schema =
        parsePropertyMap(this, depth = 0, definitions = declaredDefinitions())

    /** Output schemas are advisory, so an unconvertible one is dropped instead of hiding the tool. */
    private fun toResponseSchema(map: Map<String, Any>): Schema? {
        val converted =
            try {
                parsePropertyMap(map, depth = 0, definitions = map.declaredDefinitions())
            } catch (e: IllegalArgumentException) {
                logger.warn { "MCP tool output schema could not be converted, dropping it: ${e.message}" }
                return null
            }
        if (converted.containsAnyOf()) {
            logger.warn { "MCP tool output schema contains anyOf, which Vertex rejects; dropping it" }
            return null
        }
        return converted.takeIf { it.isTyped() }
    }

    private fun Schema.containsAnyOf(): Boolean =
        anyOf != null ||
            items?.containsAnyOf() == true ||
            properties?.values?.any { it.containsAnyOf() } == true

    private fun Map<String, Any>.declaredDefinitions(): Map<String, Any> = buildMap {
        this@declaredDefinitions["definitions"].asStringAnyMap()?.let(::putAll)
        this@declaredDefinitions["\$defs"].asStringAnyMap()?.let(::putAll)
    }

    /** Resolves local `$defs` and legacy `definitions` references. */
    private fun Map<String, Any>.resolveRef(definitions: Map<String, Any>): Map<String, Any>? {
        val ref = this["\$ref"] as? String ?: return null
        if (!ref.startsWith("#/\$defs/") && !ref.startsWith("#/definitions/")) return null
        val target = definitions[ref.substringAfterLast('/')].asStringAnyMap() ?: return null
        return target + filterKeys { it != "\$ref" }
    }

    private fun circularRef(ref: String): Schema =
        Schema(type = Type.OBJECT, description = "Circular ref to ${ref.substringAfterLast('/')}")

    /** Parses a JSON Schema type name into its ADK equivalent. */
    fun parseTypeString(typeStr: String?): Type =
        typeStr.typeOrNull() ?: throw IllegalArgumentException("Unknown type: $typeStr")

    private fun String?.typeOrNull(): Type? =
        when (this) {
            null -> Type.TYPE_UNSPECIFIED
            "string" -> Type.STRING
            "integer" -> Type.INTEGER
            "number" -> Type.NUMBER
            "boolean" -> Type.BOOLEAN
            "array" -> Type.ARRAY
            "object" -> Type.OBJECT
            "null" -> Type.NULL
            else -> null
        }

    /** Parses an arbitrary JSON Schema fragment into an ADK [Schema]. */
    fun parsePropertyMap(
        map: Map<String, Any>,
        depth: Int = 0,
        definitions: Map<String, Any> = emptyMap(),
    ): Schema = parsePropertyMap(map, depth, RefScope(definitions))

    private fun parsePropertyMap(
        map: Map<String, Any>,
        depth: Int,
        scope: RefScope,
    ): Schema {
        if (depth >= MAX_SCHEMA_DEPTH) {
            logger.warn {
                "MCP tool schema nests deeper than $MAX_SCHEMA_DEPTH levels; using an untyped object."
            }
            return Schema(type = Type.OBJECT)
        }

        val ref = map["\$ref"] as? String
        if (ref != null && ref in scope.visited) return circularRef(ref)
        map.resolveRef(scope.definitions)?.let { resolved ->
            if (!scope.spend()) {
                logger.warn {
                    "MCP tool schema expands more than $MAX_REF_EXPANSIONS references; " +
                        "using an untyped object."
                }
                return Schema(type = Type.OBJECT)
            }
            return parsePropertyMap(resolved, depth + 1, scope.following(checkNotNull(ref)))
        }
        if (map.containsKey("\$ref")) {
            logger.warn { "MCP tool schema has an unresolvable reference: ${map["\$ref"]}" }
        }

        val declared = map["type"].declaredTypes()
        val names = declared.names.filter { it.typeOrNull() != null }.ifEmpty { declared.names }
        if (names.size > 1) {
            return Schema(
                anyOf = names.map { parsePropertyMap(it.branchOf(map), depth, scope) },
                nullable = (map["nullable"] as? Boolean) ?: declared.nullable.takeIf { it },
            )
        }

        val anyOfMembers = map["anyOf"].toAnyOfSchemas(depth + 1, scope)
        val anyOfAllowsNull = anyOfMembers?.any { it.type == Type.NULL } == true
        val anyOf = anyOfMembers?.filterNot { it.type == Type.NULL }?.takeIf { it.isNotEmpty() }
        val soleAnyOfMember = anyOf?.singleOrNull()
        if (soleAnyOfMember != null && anyOfAllowsNull && map["type"] == null) {
            return soleAnyOfMember.copy(
                nullable = true,
                description = soleAnyOfMember.description ?: map["description"] as? String,
                title = soleAnyOfMember.title ?: map["title"] as? String,
                default = soleAnyOfMember.default ?: map["default"],
            )
        }

        val typeName = names.singleOrNull()
        val type = parseTypeString(typeName)
        val properties = map["properties"].asStringAnyMap().toAdkSchemaMap(depth + 1, scope)
        val isNumber = type == Type.INTEGER || type == Type.NUMBER
        val isString = type == Type.STRING
        val isArray = type == Type.ARRAY
        return Schema(
            type = type.takeIf { it != Type.TYPE_UNSPECIFIED },
            properties = properties,
            items =
                map["items"].let { items ->
                    if (items is Boolean) booleanSubSchema()
                    else items.asStringAnyMap()?.let { parsePropertyMap(it, depth + 1, scope) }
                } ?: defaultItems(type),
            required = map["required"].asStringList().requiredIn(properties),
            description = map["description"] as? String,
            enum = map["enum"].toEnumValues(),
            format = map.geminiFormat(typeName),
            nullable =
                (map["nullable"] as? Boolean)
                    ?: (declared.nullable || anyOfAllowsNull).takeIf { it },
            default = map["default"],
            anyOf = anyOf,
            title = map["title"] as? String,
            pattern = if (isString) map["pattern"] as? String else null,
            minimum = if (isNumber) map.numberAsDouble("minimum") else null,
            maximum = if (isNumber) map.numberAsDouble("maximum") else null,
            minLength = if (isString) map.numberAsLong("minLength") else null,
            maxLength = if (isString) map.numberAsLong("maxLength") else null,
            minItems = if (isArray) map.numberAsLong("minItems") else null,
            maxItems = if (isArray) map.numberAsLong("maxItems") else null,
        )
    }

    /** Required names that were dropped from `properties` must also be removed. */
    private fun List<String>?.requiredIn(properties: Map<String, Schema>?): List<String>? =
        this?.filter { properties?.containsKey(it) == true }?.takeIf { it.isNotEmpty() }

    private fun booleanSubSchema(): Schema = Schema(type = Type.OBJECT)

    /** Gemini rejects arrays without an `items` schema, so use the same safe fallback as ADK. */
    private fun defaultItems(type: Type): Schema? =
        if (type == Type.ARRAY) Schema(type = Type.STRING) else null

    private fun Map<String, Any>?.toAdkSchemaMap(
        depth: Int,
        scope: RefScope,
    ): Map<String, Schema>? =
        this
            ?.mapNotNull { (key, value) ->
                if (value is Boolean) key to booleanSubSchema()
                else value.asStringAnyMap()?.let { key to parsePropertyMap(it, depth, scope) }
            }
            ?.toMap()

    private fun Any?.declaredTypes(): DeclaredTypes =
        when (this) {
            is String -> DeclaredTypes(listOf(this), nullable = false)
            is List<*> -> {
                val names = filterIsInstance<String>()
                val realNames = names.filter { it != "null" }
                if (realNames.isEmpty()) {
                    DeclaredTypes(listOfNotNull(names.firstOrNull()), nullable = false)
                } else {
                    DeclaredTypes(realNames, nullable = realNames.size != names.size)
                }
            }
            else -> DeclaredTypes(emptyList(), nullable = false)
        }

    /** JSON Schema union members and whether the union explicitly permits null. */
    private data class DeclaredTypes(
        val names: List<String>,
        val nullable: Boolean,
    )

    private val RELATED_KEYWORDS: Map<String, List<String>> =
        mapOf(
            "number" to listOf("description", "enum", "format", "maximum", "minimum", "title"),
            "integer" to listOf("description", "enum", "format", "maximum", "minimum", "title"),
            "string" to
                listOf("description", "enum", "format", "maxLength", "minLength", "pattern", "title"),
            "object" to listOf("anyOf", "description", "properties", "required", "title"),
            "array" to listOf("description", "items", "maxItems", "minItems", "title"),
            "boolean" to listOf("description", "title"),
        )

    private fun String.branchOf(map: Map<String, Any>): Map<String, Any> = buildMap {
        put("type", this@branchOf)
        RELATED_KEYWORDS[this@branchOf]?.forEach { key -> map[key]?.let { put(key, it) } }
    }

    private fun Map<String, Any>.geminiFormat(typeName: String?): String? {
        val format = this["format"] as? String ?: return null
        return when (typeName) {
            "integer",
            "number" -> format.takeIf { it == "int32" || it == "int64" }
            "string" -> format.takeIf { it == "date-time" || it == "enum" }
            else -> null
        }
    }

    private fun Any?.toAnyOfSchemas(depth: Int, scope: RefScope): List<Schema>? =
        (this as? List<*>)
            ?.mapNotNull { member ->
                member
                    .asStringAnyMap()
                    ?.let { memberMap ->
                        try {
                            parsePropertyMap(memberMap, depth, scope)
                        } catch (e: IllegalArgumentException) {
                            logger.warn {
                                "Dropping an anyOf member that could not be converted: ${e.message}"
                            }
                            null
                        }
                    }
                    ?.takeIf { it.isTyped() }
            }
            ?.takeIf { it.isNotEmpty() }

    private fun Schema.isTyped(): Boolean =
        (type != null && type != Type.TYPE_UNSPECIFIED) ||
            properties != null ||
            items != null ||
            anyOf != null

    private fun Map<String, Any>.numberAsDouble(key: String): Double? =
        (this[key] as? Number)?.toDouble()

    private fun Map<String, Any>.numberAsLong(key: String): Long? =
        (this[key] as? Number)?.toLong()

    private fun Any?.toEnumValues(): List<String>? =
        (this as? List<*>)?.mapNotNull { it?.toString() }?.takeIf { it.isNotEmpty() }

    private fun Any?.asStringList(): List<String>? =
        (this as? List<*>)?.filterIsInstance<String>()

    private fun Any?.asStringAnyMap(): Map<String, Any>? {
        val source = this as? Map<*, *> ?: return null
        return buildMap {
            source.forEach { (key, value) ->
                if (key is String && value != null) put(key, value)
            }
        }
    }
}
