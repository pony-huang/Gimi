package github.ponyhuang.gimi.data.voice.remote

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 注册表按（服务标识，能力）解析适配器；未注册的能力是显式失败，不再静默走兜底实现。
 */
class VoiceCapabilityRegistryTest {

    @Test
    fun resolvesSynthesizerByServiceId() {
        val dashScope = synthesizer(setOf("qwen-audio"))
        val registry = VoiceCapabilityRegistry(setOf(dashScope), emptySet(), emptySet(), emptySet())

        assertSame(dashScope, registry.synthesizer("qwen-audio"))
    }

    @Test
    fun unknownServiceHasNoSynthesizer() {
        val registry = VoiceCapabilityRegistry(emptySet(), emptySet(), emptySet(), emptySet())

        val error = assertThrows(VoiceCapabilityNotSupported::class.java) {
            registry.synthesizer("never-registered")
        }
        assertEquals("never-registered", error.serviceId)
        assertEquals(ModelCapability.TTS, error.capability)
    }

    @Test
    fun recognizerResolutionDistinguishesSttFromAsr() {
        val stt = recognizer(setOf("minimax-speech"), setOf(ModelCapability.STT))
        val asr = recognizer(setOf("mimo-speech"), setOf(ModelCapability.ASR))
        val registry = VoiceCapabilityRegistry(emptySet(), setOf(stt, asr), emptySet(), emptySet())

        assertSame(stt, registry.recognizer("minimax-speech", ModelCapability.STT))
        assertSame(asr, registry.recognizer("mimo-speech", ModelCapability.ASR))
    }

    @Test
    fun serviceWithoutRequestedRecognitionCapabilityFails() {
        val asr = recognizer(setOf("mimo-speech"), setOf(ModelCapability.ASR))
        val registry = VoiceCapabilityRegistry(emptySet(), setOf(asr), emptySet(), emptySet())

        val error = assertThrows(VoiceCapabilityNotSupported::class.java) {
            registry.recognizer("mimo-speech", ModelCapability.STT)
        }
        assertEquals(ModelCapability.STT, error.capability)
    }

    @Test
    fun serviceWithoutStreamingRecognizerFails() {
        val registry = VoiceCapabilityRegistry(emptySet(), emptySet(), emptySet(), emptySet())

        assertThrows(VoiceCapabilityNotSupported::class.java) {
            registry.streamingRecognizer("minimax-speech")
        }
    }

    @Test
    fun availabilityFollowsRegisteredCapabilities() {
        val synthesizers = synthesizer(setOf("qwen-audio"))
        val recognizers = recognizer(setOf("minimax-speech"), setOf(ModelCapability.STT))
        val registry = VoiceCapabilityRegistry(setOf(synthesizers), setOf(recognizers), emptySet(), emptySet())

        assertEquals(true, registry.isSupported("qwen-audio", ModelCapability.TTS))
        assertEquals(true, registry.isSupported("minimax-speech", ModelCapability.STT))
        assertEquals(false, registry.isSupported("minimax-speech", ModelCapability.ASR))
        assertEquals(false, registry.isSupported("mimo-speech", ModelCapability.TTS))
    }

    @Test
    fun realtimeAvailabilityFollowsNativeAdapterDeclaration() {
        val support = realtimeSupport("qwen-audio")
        val registry = VoiceCapabilityRegistry(emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), setOf(support))

        // 只有声明了原生全双工适配器的服务才能选它的 Realtime 模型：
        // 其余 Realtime 标记模型会静默回落级联链路，选它只会得到起不来的会话。
        assertEquals(true, registry.isSupported("qwen-audio", ModelCapability.Realtime))
        assertEquals(false, registry.isSupported("openai-speech", ModelCapability.Realtime))
        assertSame(support, registry.realtimeSupport("qwen-audio"))
        assertNull(registry.realtimeSupport("openai-speech"))
    }
}

/** 原生 Realtime 适配器的声明替身。 */
private fun realtimeSupport(serviceId: String): RealtimeVoiceSupport {
    val support = mockk<RealtimeVoiceSupport>(relaxed = true)
    every { support.serviceId } returns serviceId
    return support
}

/** 合成适配器的服务标识声明。 */
private fun synthesizer(serviceIds: Set<String>): SpeechSynthesizer {
    val adapter = mockk<SpeechSynthesizer>(relaxed = true)
    every { adapter.serviceIds } returns serviceIds
    return adapter
}

/** 识别适配器的服务标识与能力声明。 */
private fun recognizer(
    serviceIds: Set<String>,
    capabilities: Set<ModelCapability>,
): SpeechRecognizer {
    val adapter = mockk<SpeechRecognizer>(relaxed = true)
    every { adapter.serviceIds } returns serviceIds
    every { adapter.capabilities } returns capabilities
    return adapter
}
