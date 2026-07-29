package github.ponyhuang.asssistantai.agent.tools.official

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.asssistantai.agent.ConfiguredModel
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OfficialToolsetRequestTest {

    @Test
    fun expandsToolsFromTheRequestModelConfiguration() = runTest {
        val config = config(officialTools = listOf("request_tool"))
        val request = LlmRequest(model = FakeConfiguredModel(config))
        val toolset = FakeOfficialToolset()

        val processed = toolset.processLlmRequest(mockk(relaxed = true), request)

        assertEquals(
            listOf("request_tool"),
            processed.config.tools.orEmpty()
                .flatMap { it.functionDeclarations.orEmpty() }
                .map { it.name },
        )
        assertEquals(listOf(config), toolset.seenConfigurations)
    }

    @Test
    fun leavesRequestUntouchedWhenModelDoesNotExposeConfiguration() = runTest {
        val request = LlmRequest(model = mockk<Model>(relaxed = true))

        val processed = FakeOfficialToolset()
            .processLlmRequest(mockk(relaxed = true), request)

        assertSame(request, processed)
    }

    @Test
    fun concurrentRequestsUseTheirOwnModelConfiguration() = runTest {
        val toolContext = mockk<ToolContext>(relaxed = true)
        val toolset = FakeOfficialToolset()
        val requests = listOf(
            config(officialTools = listOf("first_tool")),
            config(officialTools = listOf("second_tool")),
        ).map { config -> LlmRequest(model = FakeConfiguredModel(config)) }

        val resolvedNames = requests.map { request ->
            async {
                toolset.processLlmRequest(toolContext, request)
                    .config.tools.orEmpty()
                    .flatMap { it.functionDeclarations.orEmpty() }
                    .map { it.name }
            }
        }.awaitAll()

        assertEquals(
            listOf(listOf("first_tool"), listOf("second_tool")),
            resolvedNames,
        )
    }

    private class FakeOfficialToolset : OfficialToolset {
        val seenConfigurations = mutableListOf<ModelConfig>()

        override suspend fun resolveTools(
            config: ModelConfig,
            selection: ConversationToolConfiguration?,
        ): List<BaseTool> {
            seenConfigurations += config
            return config.officialTools.map(::DeclarationTool)
        }
    }

    private class DeclarationTool(name: String) : BaseTool(name, name) {
        override fun declaration(): FunctionDeclaration =
            FunctionDeclaration(name = name, description = description)

        override suspend fun run(
            context: ToolContext,
            args: Map<String, Any>,
        ): Any = emptyMap<String, Any>()
    }

    private class FakeConfiguredModel(
        override val modelConfig: ModelConfig,
    ) : ConfiguredModel {
        override val name: String = modelConfig.modelId

        override fun generateContent(
            request: LlmRequest,
            stream: Boolean,
        ): Flow<LlmResponse> = emptyFlow()
    }

    private fun config(
        officialTools: List<String> = emptyList(),
    ) = ModelConfig(
        serviceId = "service",
        baseType = ApiProtocol.Standard,
        modelId = "model",
        apiKey = "key",
        fullBaseUrl = "https://example.com",
        officialTools = officialTools,
    )
}
