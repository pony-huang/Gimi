package github.ponyhuang.gimi.data.modelcatalog

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 语音服务目录契约：实时与级联模型共处同厂商服务，靠能力标记分派。 */
class SpeechModelProviderTest {

    @Test
    fun `realtime models live inside their vendor speech services`() {
        // 实时语音不再是独立服务种类：并入厂商语音服务后统一一条配置。
        assertNull(SpeechModelConfigs.fromServiceId("qwen-audio-realtime"))
        assertNull(SpeechModelConfigs.fromServiceId("openai-realtime"))
        assertTrue(SpeechModelConfigs.services.all { it.kind == ModelServiceKind.Speech })
    }

    @Test
    fun `qwen speech service carries recognition synthesis and realtime groups`() {
        val provider = SpeechModelConfigs.fromServiceId("qwen-audio")!!
        assertEquals(ModelServiceKind.Speech, provider.kind)
        assertEquals(setOf("wss"), provider.endpointSchemes)
        assertEquals(SpeechEndpoints.QWEN_AUDIO_CN, provider.apiBaseUrl)
        // 不默认启用：需用户自配 key。
        assertFalse(provider.isEnabled)

        val groupIds = provider.lLMModelGroups.map { it.groupId }
        assertEquals(listOf("qwen-audio-asr", "qwen-audio-tts", "qwen-audio-realtime"), groupIds)

        val realtime = provider.lLMModelGroups.last { it.groupId == "qwen-audio-realtime" }
        assertEquals(
            listOf(
                "qwen-audio-3.1-realtime-plus",
                "qwen-audio-3.1-realtime-flash",
                "qwen-audio-3.0-realtime-plus",
                "qwen-audio-3.0-realtime-flash",
            ),
            realtime.models.map { it.modelId },
        )
        // 实时模型只携带 Realtime 标记：级联选择按 STT/TTS/ASR 过滤，天然不会拾取它们。
        assertTrue(realtime.models.all { it.capabilities == setOf(ModelCapability.Realtime) })
    }

    @Test
    fun `openai speech service carries recognition synthesis and realtime groups`() {
        val provider = SpeechModelConfigs.fromServiceId("openai-speech")!!
        assertEquals(ModelServiceKind.Speech, provider.kind)
        assertEquals(setOf("http", "https"), provider.endpointSchemes)

        val groupIds = provider.lLMModelGroups.map { it.groupId }
        assertEquals(listOf("openai-speech-stt", "openai-speech-tts", "openai-speech-realtime"), groupIds)

        val realtime = provider.lLMModelGroups.last { it.groupId == "openai-speech-realtime" }
        assertEquals(listOf("gpt-realtime-2.1", "gpt-realtime-2.1-mini"), realtime.models.map { it.modelId })
        assertTrue(realtime.models.all { it.capabilities == setOf(ModelCapability.Realtime) })
    }

    @Test
    fun `speech services declare no chat models`() {
        val chatModels = SpeechModelConfigs.services
            .flatMap { it.lLMModelGroups }
            .flatMap { it.models }
            .filter { ModelCapability.LLM in it.capabilities }
        assertTrue(chatModels.isEmpty())
    }
}
