package github.ponyhuang.asssistantai.agent.tools.official

import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.WebSearchTool20250305
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.anthropic.AnthropicOfficialToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicOfficialToolAdapterTest {

    private val adapter = AnthropicOfficialToolAdapter()
    private val toolset = AnthropicOfficialToolset()

    @Test
    fun appendsWhenEnabledAndAbsent() {
        val config = config(serviceId = "anthropic")
        val specs = toolset.anthropicNativeSpecs(config)
            .filterIsInstance<NativeToolSpec.Anthropic>()

        val adapted = adapter.adapt(config, emptyList(), specs)

        assertEquals(1, adapted.size)
        assertTrue(adapted.single().isWebSearchTool20250305())
    }

    @Test
    fun passesThroughWhenUnsupported() {
        val config = config(serviceId = "openai")
        val specs = toolset.anthropicNativeSpecs(config(serviceId = "anthropic"))
            .filterIsInstance<NativeToolSpec.Anthropic>()
        val tools = listOf(nativeWebSearch())

        val adapted = adapter.adapt(config, tools, specs)

        assertSame(tools, adapted)
    }

    @Test
    fun notSupportedWhenMismatchedProtocol() {
        val config = config(serviceId = "anthropic", baseType = ApiProtocol.Standard)

        assertFalse(adapter.supports(config))
    }

    @Test
    fun noDuplicateWhenNativeAlreadyPresent() {
        val config = config(serviceId = "anthropic")
        val specs = toolset.anthropicNativeSpecs(config)
            .filterIsInstance<NativeToolSpec.Anthropic>()
        val tools = listOf(nativeWebSearch())

        val adapted = adapter.adapt(config, tools, specs)

        assertEquals(1, adapted.size)
    }

    @Test
    fun minimaxServiceFoldedIntoParentSupports() {
        val config = config(serviceId = "minimax")
        val specs = toolset.anthropicNativeSpecs(config)
            .filterIsInstance<NativeToolSpec.Anthropic>()

        assertTrue(adapter.supports(config))
        assertEquals(1, adapter.adapt(config, emptyList(), specs).size)
    }

    @Test
    fun emptySpecsReturnsUntouched() {
        val config = config(serviceId = "anthropic")
        val tools = listOf(nativeWebSearch())

        val adapted = adapter.adapt(config, tools, emptyList())

        assertSame(tools, adapted)
    }

    private fun nativeWebSearch(): ToolUnion = ToolUnion.ofWebSearchTool20250305(
        WebSearchTool20250305.builder().build(),
    )

    private fun config(
        serviceId: String,
        baseType: ApiProtocol = ApiProtocol.Anthropic,
    ) = ModelConfig(
        serviceId = serviceId,
        baseType = baseType,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
        officialTools = listOf(OfficialToolIds.WEB_SEARCH),
    )
}
