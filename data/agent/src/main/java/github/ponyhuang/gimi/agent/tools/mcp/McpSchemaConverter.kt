package github.ponyhuang.gimi.agent.tools.mcp

import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Converts between MCP schema types and ADK types. */
internal object McpSchemaConverter {

  private val logger = LoggerFactory.getLogger(McpSchemaConverter::class)

  /** Converts an MCP [Tool] to an ADK [FunctionDeclaration]. */
  fun Tool.toAdkFunctionDeclaration(): FunctionDeclaration =
    FunctionDeclaration(
      name = name,
      description = description ?: "",
      parameters = inputSchema.toAdkSchema(),
    )

  /** Parses a type string into an ADK [Type]. */
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

  /**
   * Converts an MCP [ToolSchema] to an ADK [Schema]. A tool schema is always a JSON-Schema object;
   * only its properties can be of any type.
   */
  fun ToolSchema.toAdkSchema(): Schema =
    Schema(
      type = Type.OBJECT,
      properties = properties.toAdkProperties(),
      required = required,
      description = null,
    )

  /** Parses a JSON-Schema fragment into an ADK [Schema]. */
  fun JsonObject.toAdkSchema(): Schema =
    Schema(
      type = parseTypeString(readTypeString()),
      properties = (this["properties"] as? JsonObject).toAdkProperties(),
      items = (this["items"] as? JsonObject)?.toAdkSchema(),
      required = (this["required"] as? JsonArray)?.mapNotNull { it.contentOrNull() },
      description = this["description"].contentOrNull(),
    )

  private fun JsonObject?.toAdkProperties(): Map<String, Schema>? =
    this
      ?.mapNotNull { (key, value) -> (value as? JsonObject)?.let { key to it.toAdkSchema() } }
      ?.toMap()

  /**
   * Reads the `type` keyword, which JSON Schema allows to be either a single type or a union of
   * them.
   */
  private fun JsonObject.readTypeString(): String? =
    when (val typeValue = this["type"]) {
      is JsonArray -> {
        val typeList = typeValue.mapNotNull { it.contentOrNull() }
        if (typeList.size > 1) {
          logger.warn {
            "MCP tool schema declares a union type $typeList; ADK schemas support a single " +
              "type, so only \"${typeList.first()}\" is used and the remaining types are ignored."
          }
        }
        typeList.firstOrNull()
      }
      else -> typeValue.contentOrNull()
    }

  /** The element's string content, or `null` when it is absent, a JSON `null`, or not a primitive. */
  private fun JsonElement?.contentOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}
