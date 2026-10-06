package github.ponyhuang.gimi.domain.mcp.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class McpToolParameterTest {
    private fun tool(schema: String) = McpToolSummary("route", inputSchema = Json.parseToJsonElement(schema).jsonObject)

    @Test fun parametersPreserveRequiredNamesDescriptionsDefaultsAndEnums() {
        val parameters = tool("""{
          "type": "object", "required": ["origin"], "properties": {
            "origin": {"type":"string", "description":"起点经纬度"},
            "strategy": {"type":"integer", "default":0, "enum":[0,1,2]}
          }
        }""").parameters()
        assertEquals(listOf("origin", "strategy"), parameters.map { it.name })
        assertEquals("string", parameters[0].type)
        assertEquals("起点经纬度", parameters[0].description)
        assertTrue(parameters[0].required)
        assertFalse(parameters[1].required)
        assertEquals("integer", parameters[1].type)
        assertEquals("0", parameters[1].defaultValue)
        assertEquals(listOf("0", "1", "2"), parameters[1].enumValues)
    }

    @Test fun arrayNullableUnionAndLocalReferencesHaveReadableTypes() {
        val parameters = tool("""{
          "${'$'}defs": {"Point": {"type":"object", "properties": {"latitude": {"type":"number"}}}},
          "properties": {
            "tags": {"type":"array", "items":{"type":"string"}},
            "query": {"type":["string","null"]},
            "limit": {"anyOf":[{"type":"integer"},{"type":"string"}]},
            "point": {"${'$'}ref":"#/${'$'}defs/Point"}
          }
        }""").parameters()
        assertEquals(listOf("array<string>", "string | null", "integer | string", "object"), parameters.map { it.type })
    }

    @Test fun incompleteSchemasDoNotInventTypesOrFailOnRecursiveReferences() {
        val parameters = tool("""{
          "${'$'}defs": {"Loop": {"${'$'}ref":"#/${'$'}defs/Loop"}},
          "properties": {"anything":true, "recursive":{"${'$'}ref":"#/${'$'}defs/Loop"}, "missing": {}}
        }""").parameters()
        assertEquals(3, parameters.size)
        assertTrue(parameters.all { it.type == null && !it.required })
        assertTrue(McpToolSummary("no-schema").parameters().isEmpty())
        assertTrue(tool("{}").parameters().isEmpty())
    }
}
