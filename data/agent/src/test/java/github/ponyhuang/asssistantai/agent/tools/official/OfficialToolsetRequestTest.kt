package github.ponyhuang.asssistantai.agent.tools.official

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.asssistantai.agent.ModelRuntimeMetadata
import github.ponyhuang.asssistantai.agent.tools.ToolRunMetadata
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OfficialToolsetRequestTest {

    @Test
    fun expandsToolsFromTheInvocationRunConfig() = runTest {
        val config = config(modelId = "request_tool")
        val request = LlmRequest()
        val toolset = FakeOfficialToolset()

        val processed = toolset.processLlmRequest(toolContext(config), request)

        assertEquals(
            listOf("request_tool"),
            processed.config.tools.orEmpty()
                .flatMap { it.functionDeclarations.orEmpty() }
                .map { it.name },
        )
        assertEquals(listOf(config), toolset.seenConfigurations)
    }

    @Test
    fun leavesRequestUntouchedWhenRunConfigDoesNotExposeConfiguration() = runTest {
        val request = LlmRequest()

        val processed = FakeOfficialToolset()
            .processLlmRequest(mockk(relaxed = true), request)

        assertSame(request, processed)
    }

    @Test
    fun concurrentRequestsUseTheirOwnModelConfiguration() = runTest {
        val toolset = FakeOfficialToolset()
        val configurations = listOf(
            config(modelId = "first_tool"),
            config(modelId = "second_tool"),
        )

        val resolvedNames = configurations.map { config ->
            async {
                toolset.processLlmRequest(toolContext(config), LlmRequest())
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
        val seenConfigurations = mutableListOf<ModelRuntimeMetadata>()

        override suspend fun resolveTools(
            config: ModelRuntimeMetadata,
            selection: ConversationToolConfiguration?,
        ): List<BaseTool> {
            seenConfigurations += config
            return listOf(DeclarationTool(config.modelId))
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

    private fun toolContext(config: ModelRuntimeMetadata): ToolContext {
        val readonlyContext = mockk<ReadonlyContext>()
        every { readonlyContext.runConfig } returns RunConfig(
            customMetadata = ToolRunMetadata.of(
                modelRuntime = config,
                toolConfiguration = null,
                allowConfirmationRequiredTools = true,
            ),
        )
        return mockk {
            every { context } returns readonlyContext
        }
    }

    private fun config(
        modelId: String = "model",
    ) = ModelRuntimeMetadata(
        serviceId = "service",
        baseType = ApiProtocol.Standard,
        modelId = modelId,
        fullBaseUrl = "https://example.com",
    )
}
