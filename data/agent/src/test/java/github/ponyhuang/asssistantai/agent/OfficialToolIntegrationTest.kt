package github.ponyhuang.asssistantai.agent

import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUnion
import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionTool
import github.ponyhuang.asssistantai.agent.tools.official.KimiFormulaOfficialToolProvider
import github.ponyhuang.asssistantai.agent.tools.official.MimoWebSearchToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.MiniMaxWebSearchToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolRegistry
import github.ponyhuang.asssistantai.agent.tools.official.WebSearchOfficialToolProvider
import github.ponyhuang.asssistantai.agent.tools.official.kimi.KimiFormulaToolset
import github.ponyhuang.asssistantai.data.ApiBaseType
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialToolIntegrationTest {

    @Test
    fun mimoAdapterReplacesOnlyTheDeclaredWebSearchPlaceholder() {
        val ordinary = openAiFunctionTool("clock")
        val placeholder = openAiFunctionTool(OfficialToolIds.WEB_SEARCH)

        val adapted = MimoWebSearchToolAdapter().adapt(
            config(
                serviceId = "mimo",
                baseType = ApiBaseType.Standard,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
            listOf(ordinary, placeholder),
        )

        assertSame(ordinary, adapted[0])
        val function = adapted[1].asFunction().function()
        assertEquals(OfficialToolIds.WEB_SEARCH, function.name())
        assertEquals(
            JsonValue.from(OfficialToolIds.WEB_SEARCH),
            function._additionalProperties()["type"],
        )
        assertEquals(JsonValue.from(true), function._additionalProperties()["force_search"])
    }

    @Test
    fun mimoAdapterLeavesRequestUntouchedWhenPlaceholderIsMissing() {
        val tools = listOf(openAiFunctionTool("clock"))

        val adapted = MimoWebSearchToolAdapter().adapt(
            config(
                serviceId = "mimo",
                baseType = ApiBaseType.Standard,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
            tools,
        )

        assertSame(tools, adapted)
    }

    @Test
    fun miniMaxAdapterUsesAnthropicNativeWebSearchWithoutReorderingOtherTools() {
        val ordinary = anthropicFunctionTool("clock")
        val placeholder = anthropicFunctionTool(OfficialToolIds.WEB_SEARCH)

        val adapted = MiniMaxWebSearchToolAdapter().adapt(
            config(
                serviceId = "minimax",
                baseType = ApiBaseType.Anthropic,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
            listOf(ordinary, placeholder),
        )

        assertSame(ordinary, adapted[0])
        adapted[1].asWebSearchTool20250305()
    }

    @Test
    fun adaptersIgnoreOtherProvidersEvenWhenModelNamesLookSimilar() {
        val openAiTools = listOf(openAiFunctionTool(OfficialToolIds.WEB_SEARCH))
        val anthropicTools = listOf(anthropicFunctionTool(OfficialToolIds.WEB_SEARCH))
        val config = config(
            serviceId = "custom-proxy",
            baseType = ApiBaseType.Standard,
            officialTools = listOf(OfficialToolIds.WEB_SEARCH),
        )

        assertSame(openAiTools, MimoWebSearchToolAdapter().adapt(config, openAiTools))
        assertSame(
            anthropicTools,
            MiniMaxWebSearchToolAdapter().adapt(config, anthropicTools),
        )
    }

    @Test
    fun registryCombinesNativePlaceholdersAndAgentToolsetsFromDeclaredIds() {
        val registry = OfficialToolRegistry(
            providers = setOf(
                WebSearchOfficialToolProvider(),
                KimiFormulaOfficialToolProvider(OkHttpClient()),
            ),
        )

        val native = registry.resolve(
            config(
                serviceId = "mimo",
                baseType = ApiBaseType.Standard,
                officialTools = listOf(OfficialToolIds.WEB_SEARCH),
            ),
        )
        val agentTool = registry.resolve(
            config(
                serviceId = "kimi",
                baseType = ApiBaseType.Standard,
                officialTools = listOf(OfficialToolIds.KIMI_FORMULAS),
            ),
        )

        assertEquals(listOf(OfficialToolIds.WEB_SEARCH), native.tools.map { it.name })
        assertTrue(native.toolsets.isEmpty())
        assertTrue(agentTool.tools.isEmpty())
        assertTrue(agentTool.toolsets.single() is KimiFormulaToolset)
    }

    private fun config(
        serviceId: String,
        baseType: ApiBaseType,
        officialTools: List<String>,
    ) = ModelConfig(
        serviceId = serviceId,
        baseType = baseType,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
        officialTools = officialTools,
    )

    private fun openAiFunctionTool(name: String): ChatCompletionTool =
        ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder().name(name).build())
                .build(),
        )

    private fun anthropicFunctionTool(name: String): ToolUnion =
        ToolUnion.ofTool(
            Tool.builder()
                .name(name)
                .inputSchema(
                    Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder().build())
                        .build(),
                )
                .build(),
        )
}
