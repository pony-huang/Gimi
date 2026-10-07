package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import github.ponyhuang.gimi.data.agent.model.LocalInferenceModelFactory
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalModelRoutingTest {
    @Test fun localConfigurationUsesNativeModelWithoutProviderDeclarations() = runBlocking {
        val source = mockk<AgentModelConfigurationSource>()
        val registry = mockk<OfficialToolFunctionCatalog>()
        val native = mockk<LocalInferenceModelFactory>()
        val file = LocalModelRuntimeConfig("/private/gemma.litertlm", LocalModelBackend.GPU)
        val selection = ModelSelection(LOCAL_GEMMA_SERVICE_ID, LOCAL_GEMMA_GROUP_ID, "gpu")
        every { source.resolveChatModel(selection) } returns ResolvedAgentModel(selection.serviceId, ApiProtocol.Standard, "gpu", "", "", file)
        every { source.currentServices() } returns listOf(LocalModelCatalogState(false, listOf(
            LocalModelState(LocalModelVariant("gpu", "gemma4", "GPU", LocalModelBackend.GPU, 10), LocalModelDownloadStatus.Ready),
        )).downloadedService())
        val response = LlmResponse()
        val delegate = mockk<Model> {
            every { name } returns "gpu"
            every { generateContent(any(), true) } returns flowOf(response)
        }
        every { native.create("gpu", file) } returns delegate
        val factory = AgentLLMModelFactory(source, registry, native)
        val config = factory.selectModelConfig(selection)
        assertEquals(file, config.localModel)
        assertEquals("", config.apiKey)
        assertFalse(config.supportsImages)
        assertEquals(listOf(response), factory.createModel(config).generateContent(LlmRequest(), true).toList())
        verify(exactly = 1) { native.create("gpu", file) }
        verify(exactly = 0) { registry.providerDeclaredWireNames(any(), any(), any()) }
    }
}
