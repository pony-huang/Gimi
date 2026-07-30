package github.ponyhuang.gimi.agent.tools.official.glm

import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlmWebSearchToolsetMetadataTest {

    @Test
    fun resolvesSelectionByConfiguredServiceIdInsteadOfModelName() = runTest {
        val selection = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                SERVICE_ID to mapOf(
                    GlmWebSearchToolset.TOOL_ID to setOf(GlmWebSearchTool.NAME),
                ),
            ),
        )

        val tools = toolset().resolveTools(config(), selection)

        assertEquals(listOf(GlmWebSearchTool.NAME), tools.map { it.name })
    }

    @Test
    fun usesApiKeyFromSecureServiceConfiguration() = runTest {
        var authorization: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                authorization = chain.request().header("Authorization")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("canned")
                    .body("""{"search_result":[]}""".toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }
            .build()
        val tool = toolset(client, credential = "service-key")
            .resolveTools(config(), selection = null)
            .single { it.name == GlmWebSearchTool.NAME }

        tool.run(mockk<ToolContext>(relaxed = true), mapOf("search_query" to "test"))

        assertEquals("Bearer service-key", authorization)
    }

    @Test
    fun ignoresModelsWhoseNameIsNotGlm() = runTest {
        val tools = toolset()
            .resolveTools(config(modelId = "other-model"), selection = null)

        assertTrue(tools.isEmpty())
    }

    private fun config(
        modelId: String = "glm-4.6",
    ): ModelRuntimeMetadata = ModelRuntimeMetadata(
        serviceId = SERVICE_ID,
        baseType = ApiProtocol.Standard,
        modelId = modelId,
        fullBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
    )

    private fun toolset(
        httpClient: OkHttpClient = OkHttpClient(),
        credential: String = "key",
    ): GlmWebSearchToolset {
        val service = mockk<LLMModelSetting> {
            every { id } returns SERVICE_ID
            every { isEnabled } returns true
            every { apiKey } returns credential
        }
        val modelServices = mockk<AgentModelConfigurationSource> {
            every { currentServices() } returns listOf(service)
        }
        return GlmWebSearchToolset(httpClient, modelServices)
    }

    private companion object {
        const val SERVICE_ID = "glm"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
