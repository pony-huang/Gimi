package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import io.modelcontextprotocol.spec.McpSchema.Tool

/** Converts between MCP JSON Schema maps and ADK types. */
internal object McpSchemaConverter {

  private val logger = LoggerFactory.getLogger(McpSchemaConverter::class)

  /** Converts an MCP [Tool] to an ADK [FunctionDeclaration]. */
  fun Tool.toAdkFunctionDeclaration(): FunctionDeclaration =
    FunctionDeclaration(
      name = name(),
      description = description() ?: "",
      parameters = inputSchema().toAdkRootSchema(),
    )

  /** Parses a JSON Schema type string into an ADK [Type]. */
  fun parseTypeString(typeStr: String?): Type =
    when (typeStr) {
      null -> Type.TYPE_UNSPECIFIED
      "string" -> Type.STRING
      "integer" -> Type.INTEGER
      "number" -> Type.NUMBER
      "boolean" -> Type.BOOLEAN
      "array" -> Type.ARRAY
      "object" -> Type.OBJECT
      else -> throw IllegalArgumentException("Unknown type: $typeStr")
    }

  private fun Map<String, Any>.toAdkRootSchema(): Schema =
    Schema(
      type = Type.OBJECT,
      properties = mapValue("properties").toAdkProperties(),
      required = stringListValue("required"),
      description = null,
    )

  /** Parses a JSON Schema fragment into an ADK [Schema]. */
  private fun Map<String, Any>.toAdkSchema(): Schema =
    Schema(
      type = parseTypeString(readTypeString()),
      properties = mapValue("properties").toAdkProperties(),
      items = mapValue("items")?.toAdkSchema(),
      required = stringListValue("required"),
      description = this["description"] as? String,
    )

  private fun Map<String, Any>?.toAdkProperties(): Map<String, Schema>? =
    this?.mapNotNull { (key, value) -> value.asStringAnyMap()?.let { key to it.toAdkSchema() } }?.toMap()

  private fun Map<String, Any>.readTypeString(): String? =
    when (val value = this["type"]) {
      is List<*> -> {
        val types = value.filterIsInstance<String>()
        if (types.size > 1) {
          logger.warn {
            "MCP tool schema declares a union type $types; ADK schemas support a single type, " +
              "so only \"${types.first()}\" is used and the remaining types are ignored."
          }
        }
        types.firstOrNull()
      }
      is String -> value
      else -> null
    }

  private fun Map<String, Any>.mapValue(key: String): Map<String, Any>? =
    this[key].asStringAnyMap()

  private fun Map<String, Any>.stringListValue(key: String): List<String>? =
    (this[key] as? List<*>)?.filterIsInstance<String>()

  @Suppress("UNCHECKED_CAST")
  private fun Any?.asStringAnyMap(): Map<String, Any>? = this as? Map<String, Any>
}
