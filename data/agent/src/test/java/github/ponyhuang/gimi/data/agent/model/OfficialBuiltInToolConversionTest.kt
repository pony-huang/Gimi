package github.ponyhuang.gimi.data.agent.model

import com.anthropic.client.AnthropicClient
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Tool
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialBuiltInToolConversionTest {

    @Test
    fun anthropicConvertsWebSearchToProviderBuiltInShape() {
        val converted = TestClaude(providerBuiltInToolNames = setOf(WEB_SEARCH_TOOL_ID)).convert(
            FunctionDeclaration(
                name = WEB_SEARCH_TOOL_ID,
                description = WEB_SEARCH_TOOL_ID,
            ),
        ).single()

        assertTrue(converted.isWebSearchTool20250305())
    }

    @Test
    fun anthropicKeepsOrdinaryFunctionShape() {
        val converted = TestClaude(providerBuiltInToolNames = setOf(WEB_SEARCH_TOOL_ID)).convert(
            FunctionDeclaration(name = "local_tool", description = "Local"),
        ).single()

        assertTrue(converted.isTool())
        assertEquals("local_tool", converted.asTool().name())
    }

    @Test
    fun anthropicKeepsSameNamedExecutableFunctionShape() {
        val converted = TestClaude().convert(
            FunctionDeclaration(name = WEB_SEARCH_TOOL_ID, description = "Executable search"),
        ).single()

        assertTrue(converted.isTool())
        assertEquals(WEB_SEARCH_TOOL_ID, converted.asTool().name())
    }

    private class TestClaude(
        providerBuiltInToolNames: Set<String> = emptySet(),
    ) : Claude("test", mockk<AnthropicClient>(relaxed = true), providerBuiltInToolNames) {
        fun convert(vararg declarations: FunctionDeclaration) =
            toAnthropicTools(listOf(Tool(functionDeclarations = declarations.toList())))
    }
}

private const val WEB_SEARCH_TOOL_ID: String = "web_search"
