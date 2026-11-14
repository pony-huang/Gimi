@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.AgentChatExecution
import github.ponyhuang.gimi.data.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceConfiguration
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import github.ponyhuang.gimi.domain.voice.repository.RecognitionEvent
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession
import github.ponyhuang.gimi.domain.voice.repository.SynthesisSegment
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** 聊天模式 Agent 会话与 TTS 管线的组合行为：增量字幕、按句合成、事件顺序与收尾。 */
class AgentSessionPipelineTest {
    private val pcm = byteArrayOf(1, 0, 2, 0)
    private val selection = ModelSelection("service", "group", "model")
    private val configuration = RealtimeVoiceConfiguration(RealtimeVoiceMode.Chat, "conv", selection)

    private fun emptySimpleChatPrompts(): SimpleChatPromptRepository = object : SimpleChatPromptRepository {
        override val presets = MutableStateFlow(emptyList<SimpleChatPromptPreset>())
        override val builtInPresets = emptyList<SimpleChatPromptPreset>()
        override val selectedPresetId = MutableStateFlow<String?>(null)
        override fun upsertPreset(preset: SimpleChatPromptPreset) {}
        override fun deletePreset(presetId: String) {}
        override fun selectPreset(presetId: String?) {}
    }

    /** 聊天模式走完整 Agent；不触碰实时助手工具桥。 */
    private fun unusedAssistantTools(): RealtimeAssistantTools = mockk {
        coEvery { resolve(any()) } returns null
    }

    private fun modelText(text: String, partial: Boolean = false) = Event(
        author = AgentSessionIdentity.ASSISTANT_NAME,
        content = Content.fromText(Role.MODEL, text),
        partial = partial,
    )

    private fun scriptedExecution(send: Flow<Event>): AgentChatExecution = mockk {
        coEvery { this@mockk.send(any(), any(), any()) } returns send
    }

    private fun scriptedRunner(execution: AgentChatExecution): AgentChatRunner = mockk {
        // 语音会话以 voiceResponse = true 建立执行句柄,全参数宽松匹配。
        coEvery { createExecution(any(), any(), any(), any(), any(), any(), any(), any()) } returns execution
    }

    /** 会话式合成假件：send 缓存文本，flush 后一次性下发全部句子。 */
    private class FakeStreamingSynthesisSession(private val pcm: ByteArray) : SpeechSynthesisSession {
        val sent = mutableListOf<String>()
        val closed = CompletableDeferred<Boolean>()
        private var flushed = false

        override fun send(text: String) {
            sent += text
        }

        override suspend fun flush() {
            flushed = true
        }

        override fun cancel() {}

        override val segments: Flow<SynthesisSegment> = kotlinx.coroutines.flow.flow {
            // 与真实服务一致：片段在 flush 之后才下发完毕。
            while (!flushed) kotlinx.coroutines.delay(10)
            for (sentence in sent) emit(SynthesisSegment(MutableStateFlow(sentence), flowOf(pcm)))
        }

        override suspend fun close() {
            closed.complete(flushed)
        }
    }

    private fun backendWith(
        execution: AgentChatExecution,
        session: SpeechSynthesisSession?,
    ): AdkVoiceAudioBackend {
        val recognition = mockk<SpeechRecognitionRepository>()
        val synthesis = mockk<SpeechSynthesisRepository> {
            every { openSession() } returns session
        }
        val agentVoice = VoiceAgentConversation(
            scriptedRunner(execution), mockk(), mockk(relaxed = true), mockk(),
        )
        return AdkVoiceAudioBackend(mockk(), recognition, synthesis, emptySimpleChatPrompts(), { agentVoice }, unusedAssistantTools())
    }

    @Test
    fun streamingTurnEmitsDeltasThenPerSentenceAudioInOrder() = runBlocking {
        val ttsSession = FakeStreamingSynthesisSession(pcm)
        // ADK SSE 的 partial 事件携带增量文本；这里按增量脚本化。
        val execution = scriptedExecution(flowOf(
            modelText("你好。", partial = true),
            modelText("今天", partial = true),
            modelText("天气不错。", partial = true),
        ))
        val backend = backendWith(execution, ttsSession)
        val session = backend.open(configuration)
        val events = withTimeout(10_000) {
            session.respond("你好", "").toList()
        }
        val texts = events.filterIsInstance<VoiceAudioEvent.Text>().joinToString("") { it.text }
        assertEquals("你好。今天天气不错。", texts)
        val audio = events.filterIsInstance<VoiceAudioEvent.Audio>()
        assertEquals(listOf("你好。", "今天天气不错。"), audio.map { it.text.value })
        // 每段音频只含一段完整 PCM。
        assertEquals(listOf(listOf(pcm.toList()), listOf(pcm.toList())),
            audio.map { event -> event.audio.toList().map { chunk -> chunk.toList() } })
        assertEquals(listOf("你好。", "今天天气不错。"), ttsSession.sent)
        session.close()
    }

    @Test
    fun oneShotFallbackStillStreamsTextAndSentences() = runBlocking {
        val synthesis = mockk<SpeechSynthesisRepository> {
            every { openSession() } returns null
            every { synthesize("你好。") } returns flowOf(pcm)
            every { synthesize("今天天气不错。") } returns flowOf(pcm)
        }
        val recognition = mockk<SpeechRecognitionRepository>()
        val agentVoice = VoiceAgentConversation(
            scriptedRunner(scriptedExecution(flowOf(
                modelText("你好。", partial = true),
                modelText("今天天气不错。", partial = true),
            ))), mockk(), mockk(relaxed = true), mockk(),
        )
        val backend = AdkVoiceAudioBackend(mockk(), recognition, synthesis, emptySimpleChatPrompts(), { agentVoice }, unusedAssistantTools())
        val session = backend.open(configuration)
        val events = withTimeout(10_000) { session.respond("你好", "").toList() }
        assertEquals(listOf("你好。", "今天天气不错。"),
            events.filterIsInstance<VoiceAudioEvent.Audio>().map { it.text.value })
        assertEquals("你好。今天天气不错。", events.filterIsInstance<VoiceAudioEvent.Text>().joinToString("") { it.text })
        session.close()
    }
}
