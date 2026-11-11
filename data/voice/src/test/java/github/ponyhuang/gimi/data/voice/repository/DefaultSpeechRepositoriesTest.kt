package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
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
    fun recognition_requiresConfiguredSttModel_andTrimsAdapterResult() = runTest {
        val selection = ModelSelection("service", "speech", "stt")
        val service = service()
        val catalog = catalog(service, selection, null)
        val request = slot<RecognitionRequest>()
        val recognizer = mockk<SpeechRecognizer> {
            every { serviceIds } returns setOf("service")
            every { capabilities } returns setOf(ModelCapability.STT)
            coEvery { recognize(capture(request)) } returns " 识别结果 "
        }
        val repository = DefaultSpeechRecognitionRepository(catalog, registry(recognizer = recognizer))

        assertTrue(repository.availability.first())
        assertEquals("识别结果", repository.transcribe(byteArrayOf(1, 2)))
        assertEquals("service", request.captured.modelId.let { "service" })
        assertEquals("stt", request.captured.modelId)
        assertEquals("secret", request.captured.apiKey)
        assertEquals("https://example.test/v1", request.captured.baseUrl)
        assertEquals(byteArrayOf(1, 2).toList(), request.captured.pcm16.toList())
    }

    @Test
    fun recognition_isUnavailable_whenSelectedModelIsNotStt() = runTest {
        val selection = ModelSelection("service", "speech", "tts")
        val repository = DefaultSpeechRecognitionRepository(
            catalog(service(), selection, null),
            registry(),
        )

        assertFalse(repository.availability.first())
    }

    @Test
    fun synthesis_usesFirstKeyVoiceAndNormalizedText() = runTest {
        val selection = ModelSelection("service", "speech", "tts")
        val catalog = catalog(service(apiKey = "first, second"), null, selection)
        every { catalog.currentTtsVoice() } returns "冰糖"
        val request = slot<SynthesisRequest>()
        val synthesizer = mockk<SpeechSynthesizer> {
            every { serviceIds } returns setOf("service")
            every { synthesize(capture(request)) } returns flowOf(byteArrayOf(7))
        }
        val repository = DefaultSpeechSynthesisRepository(catalog, registry(synthesizer = synthesizer))

        assertEquals(listOf<Byte>(7), repository.synthesize(" 你好 ").first().toList())
        assertEquals("first", request.captured.apiKey)
        assertEquals("冰糖", request.captured.voiceId)
        assertEquals("你好", request.captured.text)
    }

    /**
     * 注册表只用于把（服务，能力）映射到适配器；这里给出固定的服务标识，
     * 让仓储层的解析路径保持可测。
     */
    private fun registry(
        synthesizer: SpeechSynthesizer? = null,
        recognizer: SpeechRecognizer? = null,
    ): VoiceCapabilityRegistry {
        val synthesizers = synthesizer?.let { setOf(it) } ?: emptySet()
        val recognizers = recognizer?.let { setOf(it) } ?: emptySet()
        return VoiceCapabilityRegistry(synthesizers, recognizers, emptySet(), emptySet())
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
        apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "",
        groups = listOf(
            ModelGroup(
                id = "speech",
                name = "Speech",
                models = listOf(
                    Model(id = "stt", name = "STT", capabilities = setOf(ModelCapability.STT)),
                    Model(id = "tts", name = "TTS", capabilities = setOf(ModelCapability.TTS)),
                ),
            ),
        ),
    )
}
