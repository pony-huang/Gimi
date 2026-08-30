package github.ponyhuang.gimi.data.agent.tools.official.gemini

import com.google.adk.kt.tools.GoogleMapsTool
import com.google.adk.kt.tools.GoogleSearchTool
import com.google.adk.kt.tools.UrlContextTool
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiOfficialToolsetTest {

    private val toolset = GeminiOfficialToolset()

    @Test
    fun resolvesAllBuiltInToolsForGeminiServiceByDefault() = runTest {
        val tools = toolset.resolveTools(
            config(serviceId = "gemini"),
            selection = null,
        )

        assertEquals(3, tools.size)
        assertTrue(tools.any { it is GoogleSearchTool })
        assertTrue(tools.any { it is UrlContextTool })
        assertTrue(tools.any { it is GoogleMapsTool })
    }

    @Test
    fun ignoresOtherServices() = runTest {
        val tools = toolset.resolveTools(
            config(serviceId = "openai"),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun ignoresGeminiServiceUsingNonGeminiProtocol() = runTest {
        val tools = toolset.resolveTools(
            config(
                serviceId = "gemini",
                baseType = ApiProtocol.Standard,
            ),
            selection = null,
        )

        assertTrue(tools.isEmpty())
    }

    @Test
    fun dropsToolsExcludedByConversationSelection() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "gemini" to mapOf(
                    GeminiOfficialToolset.WEB_SEARCH_TOOL_ID to
                        setOf(GeminiOfficialToolset.WEB_SEARCH_TOOL_ID),
                    GeminiOfficialToolset.URL_CONTEXT_TOOL_ID to emptySet(),
                    GeminiOfficialToolset.GOOGLE_MAPS_TOOL_ID to emptySet(),
                ),
            ),
        )

        val tools = toolset.resolveTools(config("gemini"), selection)

        // web_search 分类对应 ADK 的 GoogleSearchTool（name = "google_search"）。
        assertEquals(listOf("google_search"), tools.map { it.name })
        assertTrue(tools.single() is GoogleSearchTool)
    }

    private fun config(
        serviceId: String,
        baseType: ApiProtocol = ApiProtocol.Gemini,
    ) = ModelRuntimeMetadata(
        serviceId = serviceId,
        baseType = baseType,
        modelId = "model",
        fullBaseUrl = "https://example.com",
    )
}
