package github.ponyhuang.asssistantai.data.speech.repository

import github.ponyhuang.asssistantai.data.speech.remote.SpeechRecognitionConfig
import github.ponyhuang.asssistantai.data.speech.remote.SpeechRecognitionGateway
import github.ponyhuang.asssistantai.data.speech.remote.SpeechRecognitionRequest
import github.ponyhuang.asssistantai.data.speech.remote.SpeechSynthesisConfig
import github.ponyhuang.asssistantai.data.speech.remote.SpeechSynthesisGateway
import github.ponyhuang.asssistantai.data.speech.remote.SpeechSynthesisGatewayFactory
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSpeechRepositoriesTest {

    @Test
    fun recognition_requiresConfiguredSttModel_andTrimsGatewayResult() = runTest {
        val selection = ModelSelection("service", "speech", "stt")
        val service = service()
        val catalog = catalog(service, selection, null)
        val config = slot<SpeechRecognitionConfig>()
        val request = slot<SpeechRecognitionRequest>()
        val gateway = mockk<SpeechRecognitionGateway> {
            coEvery { transcribe(capture(config), capture(request)) } returns " 识别结果 "
        }
        val repository = DefaultSpeechRecognitionRepository(catalog, gateway)

        assertTrue(repository.availability.first())
        assertEquals("识别结果", repository.transcribe(byteArrayOf(1, 2)))
        assertEquals("https://example.test/v1", config.captured.baseUrl)
        assertEquals("secret", config.captured.apiKey)
        assertEquals("stt", config.captured.modelId)
        assertEquals(byteArrayOf(1, 2).toList(), request.captured.pcm16.toList())
    }

    @Test
    fun recognition_isUnavailable_whenSelectedModelIsNotStt() = runTest {
        val selection = ModelSelection("service", "speech", "tts")
        val repository = DefaultSpeechRecognitionRepository(
            catalog(service(), selection, null),
            mockk(relaxed = true),
        )

        assertFalse(repository.availability.first())
    }

    @Test
    fun synthesis_usesFirstKeyVoiceAndNormalizedText() = runTest {
        val selection = ModelSelection("service", "speech", "tts")
        val catalog = catalog(service(apiKey = "first, second"), null, selection)
        every { catalog.currentTtsVoice() } returns "冰糖"
        val config = slot<SpeechSynthesisConfig>()
        val text = slot<String>()
        val gateway = mockk<SpeechSynthesisGateway> {
            every { synthesize(capture(config), capture(text)) } returns flowOf(byteArrayOf(7))
        }
        val factory = mockk<SpeechSynthesisGatewayFactory> {
            every { create(any()) } returns gateway
        }
        val repository = DefaultSpeechSynthesisRepository(catalog, factory)

        assertEquals(listOf<Byte>(7), repository.synthesize(" 你好 ").first().toList())
        assertEquals("first", config.captured.apiKey)
        assertEquals("冰糖", config.captured.voiceId)
        assertEquals("你好", text.captured)
    }

    private fun catalog(
        service: LLMModelSetting,
        speechSelection: ModelSelection?,
        ttsSelection: ModelSelection?,
    ): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeServices() } returns flowOf(listOf(service))
        every { observeSpeechSelection() } returns flowOf(speechSelection)
        every { observeTtsSelection() } returns flowOf(ttsSelection)
        every { currentServices() } returns listOf(service)
        every { currentSpeechSelection() } returns speechSelection
        every { currentTtsSelection() } returns ttsSelection
        every { currentTtsVoice() } returns "mimo_default"
    }

    private fun service(apiKey: String = " secret ") = LLMModelSetting(
        id = "service",
        name = "Service",
        isEnabled = true,
        apiKey = apiKey,
        apiBaseUrl = "https://example.test/v1/",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "",
        groups = listOf(
            ModelGroup(
                id = "speech",
                name = "Speech",
                models = listOf(
                    Model(id = "stt", name = "STT", isStt = true),
                    Model(id = "tts", name = "TTS", isTts = true),
                ),
            ),
        ),
    )
}
