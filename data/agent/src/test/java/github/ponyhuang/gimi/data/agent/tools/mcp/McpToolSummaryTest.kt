package github.ponyhuang.gimi.data.agent.tools.mcp

import github.ponyhuang.gimi.domain.mcp.model.parameters
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class McpToolSummaryTest {
    @Test fun summaryReadsOriginalSchemaWithoutOpeningSessionOrConvertingDeclaration() {
        val properties = Json.parseToJsonElement("""{
            "strategy":{"type":"integer", "default":0, "enum":[0,1], "description":"路线策略"},
            "custom":{"type":"vendor-custom-type"}
        }""").jsonObject
        val tool = McpTool("route", "完整路线描述", Tool(name = "route", description = "完整路线描述",
            inputSchema = ToolSchema(properties = properties, required = listOf("strategy"))), mockk())
        val summary = tool.summary()
        assertEquals("route", summary.name)
        assertEquals("完整路线描述", summary.description)
        assertEquals(properties, summary.inputSchema?.get("properties"))
        assertEquals("0", summary.parameters().first().defaultValue)
        assertTrue(summary.parameters().first().required)
        assertEquals("vendor-custom-type", summary.parameters().last().type)
    }

    @Test fun emptyParametersRemainAnExplicitEmptySchema() {
        val tool = McpTool("ping", "", Tool(name = "ping", inputSchema = ToolSchema()), mockk())
        val summary = tool.summary()
        assertNotNull(summary.inputSchema)
        assertTrue(summary.parameters().isEmpty())
    }
}
