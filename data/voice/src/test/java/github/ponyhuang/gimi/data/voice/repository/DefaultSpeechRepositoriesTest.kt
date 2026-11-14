package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.RecognitionRequest
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.SynthesisRequest
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
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
        val selection = ModelSelection("instance", "connection", "stt")
        val instance = instance()
        val catalog = catalog(instance, selection, null)
        val request = slot<RecognitionRequest>()
        val recognizer = mockk<SpeechRecognizer> {
            every { serviceIds } returns setOf("openai")
            every { capabilities } returns setOf(ModelCapability.STT)
            coEvery { recognize(capture(request)) } returns " 识别结果 "
        }
        val repository = DefaultSpeechRecognitionRepository(catalog, registry(recognizer = recognizer))

        assertTrue(repository.availability.first())
        assertEquals("识别结果", repository.transcribe(byteArrayOf(1, 2)))
        assertEquals("stt", request.captured.modelId)
        assertEquals("secret", request.captured.apiKey)
        assertEquals("https://example.test/v1", request.captured.baseUrl)
        assertEquals(byteArrayOf(1, 2).toList(), request.captured.pcm16.toList())
    }

    @Test
    fun recognition_isUnavailable_whenSelectedModelIsNotStt() = runTest {
        val selection = ModelSelection("instance", "connection", "tts")
        val repository = DefaultSpeechRecognitionRepository(
            catalog(instance(), selection, null),
            registry(),
        )

        assertFalse(repository.availability.first())
    }

    @Test
    fun synthesis_usesFirstKeyVoiceAndNormalizedText() = runTest {
        val selection = ModelSelection("instance", "connection", "tts")
        val catalog = catalog(instance(apiKey = "first, second"), null, selection)
        every { catalog.currentTtsVoice() } returns "冰糖"
        val request = slot<SynthesisRequest>()
        val synthesizer = mockk<SpeechSynthesizer> {
            every { serviceIds } returns setOf("openai")
            every { synthesize(capture(request)) } returns flowOf(byteArrayOf(7))
        }
        val repository = DefaultSpeechSynthesisRepository(catalog, registry(synthesizer = synthesizer), voiceCatalog())

        assertEquals(listOf<Byte>(7), repository.synthesize(" 你好 ").first().toList())
        assertEquals("first", request.captured.apiKey)
        assertEquals("冰糖", request.captured.voiceId)
        assertEquals("你好", request.captured.text)
    }

    @Test
    fun synthesis_blankVoiceFallsBackToFirstBuiltInVoice() = runTest {
        val selection = ModelSelection("instance", "connection", "tts")
        val catalog = catalog(instance(), null, selection)
        every { catalog.currentTtsVoice() } returns ""
        val request = slot<SynthesisRequest>()
        val synthesizer = mockk<SpeechSynthesizer> {
            every { serviceIds } returns setOf("openai")
            every { synthesize(capture(request)) } returns flowOf(byteArrayOf(7))
        }
        val repository = DefaultSpeechSynthesisRepository(catalog, registry(synthesizer = synthesizer), voiceCatalog())

        repository.synthesize("你好").first()
        // 未配置音色时回落到当前服务内置表的第一个音色，避免无效音色触发厂商侧合成失败。
        assertEquals("v-first", request.captured.voiceId)
    }

    @Test
    fun synthesis_blankVoiceWithEmptyBuiltInTableSendsBlank() = runTest {
        val selection = ModelSelection("instance", "connection", "tts")
        val catalog = catalog(instance(), null, selection)
        every { catalog.currentTtsVoice() } returns ""
        val request = slot<SynthesisRequest>()
        val synthesizer = mockk<SpeechSynthesizer> {
            every { serviceIds } returns setOf("openai")
            every { synthesize(capture(request)) } returns flowOf(byteArrayOf(7))
        }
        val repository = DefaultSpeechSynthesisRepository(catalog, registry(synthesizer = synthesizer), voiceCatalog(empty = true))

        repository.synthesize("你好").first()
        // 无内置表的服务（千问基础音色、openaicompatible 系）按空值交给服务端默认。
        assertEquals("", request.captured.voiceId)
    }

    /**
     * 注册表只用于把（模板，能力）映射到适配器；这里给出固定的模板标识，
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

    /** 音色目录固定返回两个内置音色；empty = true 时模拟无内置表的服务。 */
    private fun voiceCatalog(empty: Boolean = false): VoiceCatalogResolver = mockk {
        every { builtInVoices(any()) } returns if (empty) {
            emptyList()
        } else {
            listOf(TtsVoice("v-first", "第一音色", "中文", "女声"), TtsVoice("v-second", "第二音色", "中文", "男声"))
        }
        every { builtInDefaultVoice(any(), any()) } returns if (empty) {
            null
        } else {
            TtsVoice("v-first", "第一音色", "中文", "女声")
        }
        every { allowsCustomVoice(any()) } returns false
        every { customVoiceIdTemplate(any()) } returns null
        every { customVoiceListUrl(any()) } returns null
    }

    private fun catalog(
        instance: ModelServiceInstance,
        speechSelection: ModelSelection?,
        ttsSelection: ModelSelection?,
    ): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeInstances() } returns flowOf(listOf(instance))
        every { observeSpeechSelection() } returns flowOf(speechSelection)
        every { observeTtsSelection() } returns flowOf(ttsSelection)
        every { currentInstances() } returns listOf(instance)
        every { currentSpeechSelection() } returns speechSelection
        every { currentTtsSelection() } returns ttsSelection
        every { currentTtsVoice() } returns ""
        every { resolveConnection(any()) } answers {
            listOf(instance).resolveConnection(firstArg())
        }
    }

    private fun instance(apiKey: String = " secret ") = ModelServiceInstance(
        id = "instance",
        name = "Service",
        templateId = "openai",
        isEnabled = true,
        isOfficialToolsEnabled = false,
        sharedApiKey = apiKey,
        connections = listOf(
            ServiceConnection(
                id = "connection",
                capability = ServiceCapability.Recognition,
                protocol = ApiProtocol.OpenaiChatcompletions,
                // 末尾斜杠会在保存期归一化剥掉；这里直接给归一化后的值。
                endpoint = "https://example.test/v1",
                models = listOf(
                    Model(id = "stt", name = "STT", capabilities = setOf(ModelCapability.STT)),
                    Model(id = "tts", name = "TTS", capabilities = setOf(ModelCapability.TTS)),
                ),
            ),
        ),
    )
}
