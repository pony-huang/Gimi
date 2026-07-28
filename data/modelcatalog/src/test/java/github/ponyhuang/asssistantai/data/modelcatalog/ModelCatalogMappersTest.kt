package github.ponyhuang.asssistantai.data.modelcatalog

import github.ponyhuang.asssistantai.domain.modelcatalog.model.MultimodalCapabilities
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogMappersTest {
    @Test
    fun legacyChatModelWithoutCapabilitiesIsExposedAsMultimodal() {
        val provider = LLMModelProvider(
            serviceId = "service",
            serviceName = "Service",
            isEnabled = true,
            apiKey = "key",
            apiBaseUrl = "https://example.com",
            lLMModelGroups = listOf(
                LLMModelGroup(
                    groupId = "group",
                    groupName = "Group",
                    models = listOf(
                        LLMModelItem(
                            modelId = "chat-model",
                            modelName = "Chat model",
                            capabilities = MultimodalCapabilities(
                                vision = null,
                                audioInput = null,
                                documentInput = null,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val capabilities = provider.toDomain().groups.single().models.single().capabilities

        assertTrue(capabilities.supportsImages)
        assertTrue(capabilities.supportsAudio)
        assertTrue(capabilities.supportsDocuments)
    }
}
