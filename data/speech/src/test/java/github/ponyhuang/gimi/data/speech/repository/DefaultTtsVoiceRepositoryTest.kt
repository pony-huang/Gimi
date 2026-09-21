package github.ponyhuang.gimi.data.speech.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.speech.model.MiMoTtsVoices
import github.ponyhuang.gimi.domain.speech.model.TtsVoice
import github.ponyhuang.gimi.domain.speech.model.TtsVoiceContext
import github.ponyhuang.gimi.domain.speech.repository.TtsVoiceProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTtsVoiceRepositoryTest {

    @Test
    fun routesToMatchingProviderAndCachesResult() = runTest {
        val testVoice = TtsVoice("v1", "Voice 1", null, null)
        val mockProvider = mockk<TtsVoiceProvider> {
            every { serviceId } returns "test_vendor"
            coEvery { getVoices(any()) } returns listOf(testVoice)
        }

        val service = LLMModelSetting(
            id = "test_vendor",
            name = "Test Vendor",
            isEnabled = true,
            apiKey = "key123",
            apiBaseUrl = "https://api.test.com",
            apiProtocol = ApiProtocol.Standard,
            anthropicBaseUrl = "",
            groups = emptyList(),
        )

        val modelCatalog = mockk<ModelCatalogRepository> {
            every { currentServices() } returns listOf(service)
        }

        val repository = DefaultTtsVoiceRepository(
            providers = setOf(mockProvider),
            modelCatalog = modelCatalog,
        )

        // First call
        val result1 = repository.getVoices("test_vendor")
        assertEquals(listOf(testVoice), result1)

        // Second call with same configuration should hit cache
        val result2 = repository.getVoices("test_vendor")
        assertEquals(listOf(testVoice), result2)

        coVerify(exactly = 1) { mockProvider.getVoices(match { it.serviceId == "test_vendor" && it.apiKey == "key123" }) }
    }

    @Test
    fun fallbacksToCatalogWhenNoProviderMatches() = runTest {
        val modelCatalog = mockk<ModelCatalogRepository> {
            every { currentServices() } returns emptyList()
        }

        val repository = DefaultTtsVoiceRepository(
            providers = emptySet(),
            modelCatalog = modelCatalog,
        )

        val voices = repository.getVoices("mimo")
        assertEquals(MiMoTtsVoices.all.map { it.id }, voices.map { it.id })
    }
}
