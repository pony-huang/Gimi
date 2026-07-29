package github.ponyhuang.asssistantai.agent.tools.mcp

import com.google.adk.kt.types.Type
import github.ponyhuang.asssistantai.agent.tools.mcp.McpSchemaConverter.toAdkFunctionDeclaration
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class McpSchemaConverterTest {

  @Test
  fun `converts tool with primitive properties`() {
    val tool =
      tool(
        inputSchema =
          ToolSchema(
            properties =
              buildJsonObject {
                putJsonObject("city") {
                  put("type", "string")
                  put("description", "City to look up.")
                }
                putJsonObject("days") { put("type", "integer") }
              },
            required = listOf("city"),
          )
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
          ToolSchema(
            properties =
              buildJsonObject {
                putJsonObject("tags") {
                  put("type", "array")
                  putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("filter") {
                  put("type", "object")
                  putJsonObject("properties") { putJsonObject("q") { put("type", "string") } }
                  putJsonArray("required") { add(JsonPrimitive("q")) }
                }
              }
          )
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
  fun `keeps only the first type of a union`() {
    val tool =
      tool(
        inputSchema =
          ToolSchema(
            properties =
              buildJsonObject {
                putJsonObject("note") {
                  putJsonArray("type") {
                    add(JsonPrimitive("string"))
                    add(JsonPrimitive("null"))
                  }
                }
              }
          )
      )

    val parameters = tool.toAdkFunctionDeclaration().parameters!!

    assertEquals(Type.STRING, parameters.properties!!["note"]!!.type)
  }

  @Test
  fun `defaults a property without a type to unspecified`() {
    val tool =
      tool(
        inputSchema =
          ToolSchema(properties = buildJsonObject { putJsonObject("anything") { put("x", 1) } })
      )

    val parameters = tool.toAdkFunctionDeclaration().parameters!!

    assertEquals(Type.TYPE_UNSPECIFIED, parameters.properties!!["anything"]!!.type)
  }

  @Test
  fun `converts a schema without properties`() {
    val parameters = tool(inputSchema = ToolSchema()).toAdkFunctionDeclaration().parameters!!

    assertEquals(Type.OBJECT, parameters.type)
    assertNull(parameters.properties)
    assertNull(parameters.required)
  }

  @Test
  fun `rejects an unknown property type`() {
    val tool =
      tool(
        inputSchema =
          ToolSchema(
            properties = buildJsonObject { putJsonObject("weird") { put("type", "tuple") } }
          )
      )

    assertThrows(IllegalArgumentException::class.java) { tool.toAdkFunctionDeclaration() }
  }

  @Test
  fun `falls back to an empty description`() {
    val declaration =
      Tool(name = "no_description", inputSchema = ToolSchema()).toAdkFunctionDeclaration()

    assertEquals("", declaration.description)
  }

  private fun tool(inputSchema: ToolSchema): Tool =
    Tool(
      name = "get_weather",
      inputSchema = inputSchema,
      description = "Returns the weather.",
    )
}
