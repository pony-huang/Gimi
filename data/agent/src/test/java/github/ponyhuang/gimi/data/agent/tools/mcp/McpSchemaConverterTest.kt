package github.ponyhuang.gimi.data.agent.tools.mcp

import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.data.agent.tools.mcp.McpSchemaConverter.toAdkFunctionDeclaration
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class McpSchemaConverterTest {

  @Test
  fun `converts tool with primitive properties`() {
    val tool =
      tool(
        inputSchema =
          schema(
            properties =
              mapOf(
                "city" to mapOf("type" to "string", "description" to "City to look up."),
                "days" to mapOf("type" to "integer"),
              ),
            required = listOf("city"),
          ),
      )

    val declaration = tool.toAdkFunctionDeclaration()

    assertEquals("get_weather", declaration.name)
    assertEquals("Returns the weather.", declaration.description)
    val parameters = declaration.parameters!!
    assertEquals(Type.OBJECT, parameters.type)
    assertEquals(listOf("city"), parameters.required)
    assertEquals(Type.STRING, parameters.properties!!["city"]!!.type)
    assertEquals("City to look up.", parameters.properties!!["city"]!!.description)
    assertEquals(Type.INTEGER, parameters.properties!!["days"]!!.type)
  }

  @Test
  fun `converts nested object and array properties`() {
    val tool =
      tool(
        inputSchema =
          schema(
            properties =
              mapOf(
                "tags" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                "filter" to
                  mapOf(
                    "type" to "object",
                    "properties" to mapOf("q" to mapOf("type" to "string")),
                    "required" to listOf("q"),
                  ),
              ),
          ),
      )

    val parameters = tool.toAdkFunctionDeclaration().parameters!!

    val tags = parameters.properties!!["tags"]!!
    assertEquals(Type.ARRAY, tags.type)
    assertEquals(Type.STRING, tags.items!!.type)

    val filter = parameters.properties!!["filter"]!!
    assertEquals(Type.OBJECT, filter.type)
    assertEquals(Type.STRING, filter.properties!!["q"]!!.type)
    assertEquals(listOf("q"), filter.required)
  }

  @Test
  fun `keeps every supported type of a union`() {
    val tool =
      tool(
        inputSchema =
          schema(properties = mapOf("note" to mapOf("type" to listOf("string", "integer")))),
      )

    val parameters = tool.toAdkFunctionDeclaration().parameters!!

    assertEquals(
      listOf(Type.STRING, Type.INTEGER),
      parameters.properties!!["note"]!!.anyOf!!.map { it.type },
    )
  }

  @Test
  fun `leaves a property without a type unset`() {
    val tool =
      tool(
        inputSchema =
          schema(properties = mapOf("anything" to mapOf("x" to 1)))
      )

    val parameters = tool.toAdkFunctionDeclaration().parameters!!

    assertNull(parameters.properties!!["anything"]!!.type)
  }

  @Test
  fun `converts a schema without properties`() {
    val parameters = tool(inputSchema = schema()).toAdkFunctionDeclaration().parameters!!

    assertEquals(Type.OBJECT, parameters.type)
    assertNull(parameters.properties)
    assertNull(parameters.required)
  }

  @Test
  fun `rejects an unknown property type`() {
    val tool =
      tool(
        inputSchema =
          schema(properties = mapOf("weird" to mapOf("type" to "tuple")))
      )

    assertThrows(IllegalArgumentException::class.java) { tool.toAdkFunctionDeclaration() }
  }

  @Test
  fun `falls back to an empty description`() {
    val declaration =
      tool(inputSchema = schema(), description = null).toAdkFunctionDeclaration()

    assertEquals("", declaration.description)
  }

  @Test
  fun `preserves supported constraints and output schema`() {
    val tool =
      Tool(
          name = "get_weather",
          inputSchema = schema(
            properties =
              mapOf(
                "city" to
                  mapOf(
                    "type" to "string",
                    "enum" to listOf("Shanghai", "Beijing"),
                    "pattern" to "^[A-Za-z]+$",
                    "minLength" to 2,
                    "maxLength" to 32,
                  ),
              ),
          ),
          description = "Returns the weather.",
          outputSchema =
            schema(
              properties = mapOf("temperature" to mapOf("type" to "number")),
            ),
        )

    val declaration = tool.toAdkFunctionDeclaration()

    val city = declaration.parameters!!.properties!!["city"]!!
    assertEquals(listOf("Shanghai", "Beijing"), city.enum)
    assertEquals("^[A-Za-z]+$", city.pattern)
    assertEquals(2L, city.minLength)
    assertEquals(32L, city.maxLength)
    assertNotNull(declaration.response)
    assertEquals(Type.NUMBER, declaration.response!!.properties!!["temperature"]!!.type)
  }

  private fun tool(
    inputSchema: ToolSchema,
    description: String? = "Returns the weather.",
  ): Tool = Tool(name = "get_weather", inputSchema = inputSchema, description = description)

  private fun schema(
    properties: Map<String, Any>? = null,
    required: List<String>? = null,
  ): ToolSchema =
    ToolSchema(
      properties = properties?.let { values ->
        JsonObject(values.mapValues { (_, value) -> value.toJsonElement() })
      },
      required = required,
    )

  private fun Any?.toJsonElement(): JsonElement =
    when (this) {
      null -> JsonNull
      is Map<*, *> ->
        JsonObject(
          entries.associate { (key, value) -> key.toString() to value.toJsonElement() },
        )
      is List<*> -> JsonArray(map { it.toJsonElement() })
      is Boolean -> JsonPrimitive(this)
      is Number -> JsonPrimitive(this)
      else -> JsonPrimitive(toString())
    }
}
