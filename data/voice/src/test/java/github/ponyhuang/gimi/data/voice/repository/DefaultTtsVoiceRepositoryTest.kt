package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoTtsVoices
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceContext
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver

class DefaultTtsVoiceRepositoryTest {

    @Test
    fun routesToMatchingProviderAndCachesResult() = runTest {
        val testVoice = TtsVoice("v1", "Voice 1", null, null)
        val mockProvider = mockk<TtsVoiceProvider> {
            every { serviceIds } returns setOf("test_vendor")
            coEvery { getVoices(any()) } returns listOf(testVoice)
        }

        val modelCatalog = modelCatalog(
            listOf(instance(templateId = "test_vendor", apiKey = "key123", modelIds = listOf("m1"))),
        )

        val repository = DefaultTtsVoiceRepository(
            providers = setOf(mockProvider),
            modelCatalog = modelCatalog,
            voiceCatalog = mockk(relaxed = true),
        )

        // First call
        val result1 = repository.getVoices(selection())
        assertEquals(listOf(testVoice), result1)

        // Second call with same configuration should hit cache
        val result2 = repository.getVoices(selection())
        assertEquals(listOf(testVoice), result2)

        coVerify(exactly = 1) {
            mockProvider.getVoices(match { it.serviceId == "test_vendor" && it.apiKey == "key123" && it.modelId == "m1" })
        }
    }

    @Test
    fun fallbacksToCatalogWhenNoProviderMatches() = runTest {
        val modelCatalog = modelCatalog(
            listOf(instance(templateId = "mimo", apiKey = "key", instanceId = "instance-mimo", modelIds = listOf("m1"))),
        )

        val voiceCatalog = mockk<VoiceCatalogResolver> {
            every { builtInVoices("mimo") } returns MiMoTtsVoices.all
        }

        val repository = DefaultTtsVoiceRepository(
            providers = emptySet(),
            modelCatalog = modelCatalog,
            voiceCatalog = voiceCatalog,
        )

        val voices = repository.getVoices(ModelSelection(instanceId = "instance-mimo", connectionId = "connection", modelId = "m1"))
        assertEquals(MiMoTtsVoices.all.map { it.id }, voices.map { it.id })
    }

    @Test
    fun cachesPerModelSoSwitchingModelDoesNotReuseAnotherModelsVoices() = runTest {
        val mockProvider = mockk<TtsVoiceProvider> {
            every { serviceIds } returns setOf("test_vendor")
            coEvery { getVoices(any()) } answers {
                listOf(TtsVoice("voice-${firstArg<TtsVoiceContext>().modelId}", "Voice", null, null))
            }
        }
        val repository = DefaultTtsVoiceRepository(
            setOf(mockProvider),
            modelCatalog(
                listOf(
                    instance(
                        templateId = "test_vendor", apiKey = "key123",
                        modelIds = listOf("model-a", "model-b"),
                    ),
                ),
            ),
            voiceCatalog = mockk(relaxed = true),
        )

        val first = repository.getVoices(ModelSelection("instance-vendor", "connection", "model-a"))
        val second = repository.getVoices(ModelSelection("instance-vendor", "connection", "model-b"))

        assertEquals(listOf("voice-model-a"), first.map { it.id })
        assertEquals(listOf("voice-model-b"), second.map { it.id })
    }

    private fun modelCatalog(instances: List<ModelServiceInstance>): ModelCatalogRepository =
        mockk {
            every { currentInstances() } returns instances
            every { resolveConnection(any()) } answers {
                instances.resolveConnection(firstArg())
            }
        }

    private fun instance(
        templateId: String,
        apiKey: String,
        instanceId: String = "instance-vendor",
        modelIds: List<String> = emptyList(),
    ) = ModelServiceInstance(
        id = instanceId,
        name = "Test Vendor",
        templateId = templateId,
        isEnabled = true,
        isOfficialToolsEnabled = false,
        sharedApiKey = apiKey,
        connections = listOf(
            ServiceConnection(
                id = "connection",
                capability = ServiceCapability.Synthesis,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://api.test.com",
                models = modelIds.map { Model(it, it) },
            ),
        ),
    )

    private fun selection() = ModelSelection(instanceId = "instance-vendor", connectionId = "connection", modelId = "m1")
}
