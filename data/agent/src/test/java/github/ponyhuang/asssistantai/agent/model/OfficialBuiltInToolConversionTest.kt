package github.ponyhuang.asssistantai.agent.model

import com.anthropic.client.AnthropicClient
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Tool
import com.openai.client.OpenAIClient
import com.openai.core.jsonMapper
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialBuiltInToolConversionTest {

    @Test
    fun openAiConvertsWebSearchToProviderBuiltInShape() {
        val converted = TestOpenai().convert(
            FunctionDeclaration(
                name = OfficialToolIds.WEB_SEARCH,
                description = OfficialToolIds.WEB_SEARCH,
            ),
        ).single()

        val json = jsonMapper().writeValueAsString(converted)
        assertTrue(json.contains("\"type\":\"${OfficialToolIds.WEB_SEARCH}\""))
    }

    @Test
    fun openAiKeepsOrdinaryFunctionShape() {
        val converted = TestOpenai().convert(
            FunctionDeclaration(name = "local_tool", description = "Local"),
        ).single()

        val json = jsonMapper().writeValueAsString(converted)
        assertTrue(json.contains("\"type\":\"function\""))
        assertEquals("local_tool", converted.asFunction().function().name())
    }

    @Test
    fun anthropicConvertsWebSearchToProviderBuiltInShape() {
        val converted = TestClaude().convert(
            FunctionDeclaration(
                name = OfficialToolIds.WEB_SEARCH,
                description = OfficialToolIds.WEB_SEARCH,
            ),
        ).single()

        assertTrue(converted.isWebSearchTool20250305())
    }

    @Test
    fun anthropicKeepsOrdinaryFunctionShape() {
        val converted = TestClaude().convert(
            FunctionDeclaration(name = "local_tool", description = "Local"),
        ).single()

        assertTrue(converted.isTool())
        assertEquals("local_tool", converted.asTool().name())
    }

    private class TestOpenai : Openai("test", mockk<OpenAIClient>(relaxed = true)) {
        fun convert(vararg declarations: FunctionDeclaration) =
            toOpenAiTools(listOf(Tool(functionDeclarations = declarations.toList())))
    }

    private class TestClaude : Claude("test", mockk<AnthropicClient>(relaxed = true)) {
        fun convert(vararg declarations: FunctionDeclaration) =
            toAnthropicTools(listOf(Tool(functionDeclarations = declarations.toList())))
    }
}
