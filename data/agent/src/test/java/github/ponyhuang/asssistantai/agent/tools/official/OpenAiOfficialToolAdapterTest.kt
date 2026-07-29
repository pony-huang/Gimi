package github.ponyhuang.asssistantai.agent.tools.official

import com.openai.models.FunctionDefinition
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.openai.OpenaiOfficialToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiOfficialToolAdapterTest {

    private val adapter = OpenAiOfficialToolAdapter()
    private val toolset = OpenaiOfficialToolset()

    @Test
    fun appendsWhenEnabledAndAbsent() {
        val config = config(serviceId = "openai")
        val specs = toolset.openAiNativeSpecs(config)
            .filterIsInstance<NativeToolSpec.OpenAi>()

        val adapted = adapter.adapt(config, emptyList(), specs)

        assertEquals(1, adapted.size)
        assertEquals(OfficialToolIds.WEB_SEARCH, adapted.single().asFunction().function().name())
    }

    @Test
    fun passesThroughWhenUnsupported() {
        val config = config(serviceId = "kimi")
        val specs = toolset.openAiNativeSpecs(config(serviceId = "openai"))
            .filterIsInstance<NativeToolSpec.OpenAi>()
        val tools = listOf(functionTool("local_tool"))

        val adapted = adapter.adapt(config, tools, specs)

        assertSame(tools, adapted)
    }

    @Test
    fun notSupportedWhenMismatchedProtocol() {
        val config = config(serviceId = "openai", baseType = ApiProtocol.Anthropic)

        assertFalse(adapter.supports(config))
    }

    @Test
    fun noDuplicateWhenNativeAlreadyPresent() {
        val config = config(serviceId = "openai")
        val specs = toolset.openAiNativeSpecs(config)
            .filterIsInstance<NativeToolSpec.OpenAi>()
        val tools = listOf(functionTool(OfficialToolIds.WEB_SEARCH))

        val adapted = adapter.adapt(config, tools, specs)

        assertEquals(1, adapted.size)
    }

    @Test
    fun mimoServiceFoldedIntoParentSupports() {
        val config = config(serviceId = "mimo")
        val specs = toolset.openAiNativeSpecs(config)
            .filterIsInstance<NativeToolSpec.OpenAi>()

        assertTrue(adapter.supports(config))
        assertEquals(1, adapter.adapt(config, emptyList(), specs).size)
    }

    @Test
    fun emptySpecsReturnsUntouched() {
        val config = config(serviceId = "openai")
        val tools = listOf(functionTool("local_tool"))

        val adapted = adapter.adapt(config, tools, emptyList())

        assertSame(tools, adapted)
    }

    private fun functionTool(name: String): ChatCompletionTool =
        ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder().name(name).build())
                .build(),
        )

    private fun config(
        serviceId: String,
        baseType: ApiProtocol = ApiProtocol.Standard,
    ) = ModelConfig(
        serviceId = serviceId,
        baseType = baseType,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
        officialTools = listOf(OfficialToolIds.WEB_SEARCH),
    )
}
