package github.ponyhuang.gimi.data.modelcatalog

import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogMappersTest {
    @Test
    fun chatCompletionsProtocolRoundTripsThroughStorageAndDomain() {
        val provider = LLMModelConfigs.services.single { it.serviceId == "openai" }

        val encoded = modelCatalogJson.encodeToString(ApiBaseType.serializer(), provider.baseType)
        assertEquals("\"OpenaiChatcompletions\"", encoded)
        val restored = modelCatalogJson.decodeFromString(ApiBaseType.serializer(), encoded)
        val domain = provider.copy(baseType = restored).toDomain()

        assertEquals("OpenaiChatcompletions", domain.apiProtocol.name)
        assertEquals(restored, domain.apiProtocol.toData())
        assertEquals(provider.apiBaseUrl, domain.activeApiBaseUrl)
    }

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
