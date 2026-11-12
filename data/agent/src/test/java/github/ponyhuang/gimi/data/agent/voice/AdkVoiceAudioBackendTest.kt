@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.models.LiveConnection
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Transcription
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RecognitionEvent
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException
import github.ponyhuang.gimi.domain.voice.repository.VoiceAudioEvent
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

/** 后端只读预设状态；测试提供空仓库表示不使用自定义提示词。 */
private fun emptySimpleChatPrompts(): SimpleChatPromptRepository = object : SimpleChatPromptRepository {
    override val presets = MutableStateFlow(emptyList<SimpleChatPromptPreset>())
    override val builtInPresets = emptyList<SimpleChatPromptPreset>()
    override val selectedPresetId = MutableStateFlow<String?>(null)
    override fun upsertPreset(preset: SimpleChatPromptPreset) {}
    override fun deletePreset(presetId: String) {}
    override fun selectPreset(presetId: String?) {}
}

/** 聊天模式的完整 Agent 执行层由 [VoiceAgentConversationTest] 单独覆盖；简单聊天用例不会触碰它。 */
private fun unusedAgentVoice(): javax.inject.Provider<VoiceAgentConversation> =
    javax.inject.Provider { error("Voice agent is not used in simple mode tests") }

/** 级联/简单聊天用例不触碰实时助手工具桥；resolve 一律返回 null。 */
private fun unusedAssistantTools(): RealtimeAssistantTools = mockk {
    coEvery { resolve(any()) } returns null
}

/** 保留简单聊天级联后端的付费请求、已听上下文和取消边界，并覆盖官方原生事件顺序。 */
class AdkVoiceAudioBackendTest {
    private val recognition = mockk<SpeechRecognitionRepository>()
    private val synthesis = mockk<SpeechSynthesisRepository> {
        // 默认走一次性合成：会话式合成由专门的用例覆盖。
        every { openSession() } returns null
    }
    private val textModel = mockk<Model>()
    private val models = mockk<VoiceLiveModelFactory>()
    private val pcm = byteArrayOf(1, 0, 2, 0)

    @Test
    fun pcmInputTextDeltasAndLazySynthesisKeepTheirExistingBehavior(): Unit = runBlocking {
        withTimeout(5_000) {
            every { recognition.transcribeStreaming(pcm) } returns flowOf(RecognitionEvent.Final(" 用户输入 "))
            every { textModel.name } returns "fake"
            every { textModel.generateContent(any(), true) } answers {
                // 实时语音不能关闭模型必需的思考模式；thought 内容仍由语音层过滤。
                assertNull(firstArg<LlmRequest>().config.thinkingConfig)
                flowOf(
                    LlmResponse(content = Content(role = Role.MODEL, parts = listOf(
                        Part(text = "内部推理", thought = true),
                    )), partial = true),
                    text("第一句。", true), text("尾句", true), text("第一句。尾句", false),
                )
            }
            val requested = mutableListOf<String>()
            every { synthesis.synthesize(any()) } answers {
                requested += firstArg<String>()
                flowOf(pcm)
            }
            coEvery { models.create() } answers { CascadedVoiceModel(textModel, recognition, synthesis) }
            val session = AdkVoiceAudioBackend(models, recognition, synthesis, emptySimpleChatPrompts(), unusedAgentVoice(), unusedAssistantTools()).open()
            val text = StringBuilder()
            val spoken = mutableListOf<String>()
            var firstAudio = true
            coroutineScope {
                session.respond(session.transcribe(pcm).filterIsInstance<RecognitionEvent.Final>().last().text, "").collect { event ->
                    when (event) {
                        VoiceAudioEvent.Interrupted -> fail("Unexpected interruption")
                        is VoiceAudioEvent.Text -> text.append(event.text)
                        is VoiceAudioEvent.Audio -> {
                            if (firstAudio) {
                                assertTrue(requested.isEmpty())
                                firstAudio = false
                            }
                            launch {
                                assertArrayEquals(pcm, event.audio.toList().single())
                                spoken += event.text.value
                            }
                        }
                    }
                }
            }
            assertEquals("第一句。尾句", text.toString())
            assertEquals(listOf("第一句。", "尾句"), spoken)
            assertEquals(spoken, requested)
            session.close()
            session.close()
            assertThrows(IllegalStateException::class.java) { runBlocking { session.transcribe(pcm).toList() } }
        }
    }

    @Test
    fun interruptedReplySeedsOnlyTheActuallyHeardSentencesIntoTheNextConnection() = runBlocking {
        withTimeout(5_000) {
            val requests = mutableListOf<LlmRequest>()
            every { textModel.name } returns "fake"
            every { textModel.generateContent(any(), true) } answers {
                requests += firstArg<LlmRequest>()
                flowOf(text("已听。未听。", true))
            }
            val unplayedStarted = CompletableDeferred<Unit>()
            every { synthesis.synthesize(any()) } answers {
                if (firstArg<String>() == "未听。") flow {
                    unplayedStarted.complete(Unit)
                    awaitCancellation()
                } else flowOf(pcm)
            }
            coEvery { models.create() } answers { CascadedVoiceModel(textModel, recognition, synthesis) }
            val session = AdkVoiceAudioBackend(models, recognition, synthesis, emptySimpleChatPrompts(), unusedAgentVoice(), unusedAssistantTools()).open()
            val first = launch {
                coroutineScope {
                    session.respond("上一轮", "").collect { event ->
                        if (event is VoiceAudioEvent.Audio) launch { event.audio.collect() }
                    }
                }
            }
            unplayedStarted.await()
            first.cancelAndJoin()
            every { textModel.generateContent(any(), true) } answers {
                requests += firstArg<LlmRequest>()
                emptyFlow()
            }
            session.respond("下一轮", "已听。").collect()
            val contents = requests.last().contents.map { it.parts.mapNotNull { part -> part.text }.joinToString("") }
            assertEquals(listOf("上一轮", "已听。", "下一轮"), contents)
            session.close()
        }
    }

    @Test
    fun interruptionBeforeAnyPlaybackKeepsBothUserInputsWithoutReplayingTheOldQuestion() = runBlocking {
        withTimeout(5_000) {
            val requests = mutableListOf<LlmRequest>()
            every { textModel.name } returns "fake"
            every { textModel.generateContent(any(), true) } answers {
                requests += firstArg<LlmRequest>()
                flowOf(text("未听。", true))
            }
            val started = CompletableDeferred<Unit>()
            every { synthesis.synthesize(any()) } returns flow {
                started.complete(Unit)
                awaitCancellation()
            }
            coEvery { models.create() } answers { CascadedVoiceModel(textModel, recognition, synthesis) }
            val session = AdkVoiceAudioBackend(models, recognition, synthesis, emptySimpleChatPrompts(), unusedAgentVoice(), unusedAssistantTools()).open()
            val first = launch {
                coroutineScope {
                    session.respond("旧问题", "").collect { event ->
                        if (event is VoiceAudioEvent.Audio) launch { event.audio.collect() }
                    }
                }
            }
            started.await()
            first.cancelAndJoin()
            every { textModel.generateContent(any(), true) } answers {
                requests += firstArg<LlmRequest>()
                emptyFlow()
            }
            session.respond("新问题", "").collect()
            assertEquals(2, requests.size)
            assertEquals(listOf("旧问题", "新问题"), requests.last().contents.map { it.parts.single().text })
            assertTrue(requests.last().contents.all { it.role == Role.USER })
            session.close()
        }
    }

    @Test
    fun nativeAudioMayPrecedeItsTranscriptAndFinishingGenerationDoesNotLoseIt() = runBlocking {
        withTimeout(5_000) {
            val connection = mockk<LiveConnection>()
            coEvery { connection.sendHistory(any()) } just Runs
            coEvery { connection.sendContent(any(), any()) } just Runs
            coEvery { connection.closeSession() } just Runs
            every { connection.receive() } returns flowOf(
                LlmResponse(content = Content(role = Role.MODEL, parts = listOf(
                    Part(inlineData = Blob(mimeType = "audio/pcm;rate=24000", data = pcm)),
                )), partial = true),
                LlmResponse(outputTranscription = Transcription(text = "回答", finished = false)),
                LlmResponse(outputTranscription = Transcription(text = "内容", finished = true)),
                LlmResponse(turnComplete = true),
            )
            val native = mockk<Model>()
            coEvery { native.connect(any()) } returns connection
            coEvery { models.create() } returns native
            val session = AdkVoiceAudioBackend(models, recognition, synthesis, emptySimpleChatPrompts(), unusedAgentVoice(), unusedAssistantTools()).open()
            var heard = ""
            val visible = StringBuilder()
            coroutineScope {
                session.respond("输入", "").collect { event ->
                    when (event) {
                        VoiceAudioEvent.Interrupted -> fail("Unexpected interruption")
                        is VoiceAudioEvent.Text -> visible.append(event.text)
                        is VoiceAudioEvent.Audio -> launch {
                            assertArrayEquals(pcm, event.audio.toList().single())
                            heard = event.text.value
                        }
                    }
                }
            }
            assertEquals("回答内容", heard)
            assertEquals(heard, visible.toString())
            coVerify(exactly = 1) { connection.closeSession() }
        }
    }

    @Test
    fun synchronousSynthesisFailureKeepsItsStage() = runBlocking {
        withTimeout(5_000) {
            every { textModel.name } returns "fake"
            every { textModel.generateContent(any(), true) } returns flowOf(text("回答。", true))
            every { synthesis.synthesize(any()) } throws IllegalStateException("unavailable")
            coEvery { models.create() } answers { CascadedVoiceModel(textModel, recognition, synthesis) }
            val session = AdkVoiceAudioBackend(models, recognition, synthesis, emptySimpleChatPrompts(), unusedAgentVoice(), unusedAssistantTools()).open()
            try {
                coroutineScope {
                    session.respond("输入", "").collect { event ->
                        if (event is VoiceAudioEvent.Audio) launch { event.audio.collect() }
                    }
                }
                fail("Expected synthesis failure")
            } catch (error: VoiceAudioBackendException) {
                assertEquals(RealtimeVoiceFailure.Synthesis, error.failure)
            }
            session.close()
        }
    }

    private fun text(value: String, partial: Boolean): LlmResponse =
        LlmResponse(content = Content.fromText(Role.MODEL, value), partial = partial)

    @Test
    fun serverInterruptionIsAPlaybackEventAndStillClosesTheConnection() = runBlocking {
        val connection = mockk<LiveConnection>()
        coEvery { connection.sendHistory(any()) } just Runs
        coEvery { connection.sendContent(any(), any()) } just Runs
        coEvery { connection.closeSession() } just Runs
        every { connection.receive() } returns flowOf(LlmResponse(interrupted = true, turnComplete = true))
        val native = mockk<Model>()
        coEvery { native.connect(any()) } returns connection
        coEvery { models.create() } returns native
        val events = AdkVoiceAudioBackend(models, recognition, synthesis, emptySimpleChatPrompts(), unusedAgentVoice(), unusedAssistantTools()).open().respond("输入", "").toList()
        assertEquals(listOf(VoiceAudioEvent.Interrupted), events)
        coVerify(exactly = 1) { connection.closeSession() }
    }

    /** 助手模式不能被「无工具」绝对声明固化：一次工具面故障不该让模型长期自称没有工具。 */
    @Test
    fun assistantModeNeverTellsTheModelItHasNoTools() {
        val withTools = AdkVoiceAudioBackend.realtimeInstructions(
            preset = null, assistantMode = true, hasDeclarations = true,
        )
        assertTrue("You can call the tools" in withTools)
        assertFalse("You have no tools" in withTools)

        val withoutTools = AdkVoiceAudioBackend.realtimeInstructions(
            preset = null, assistantMode = true, hasDeclarations = false,
        )
        assertFalse("You have no tools" in withoutTools)
        assertTrue("never invent a completed action" in withoutTools)
    }

    /** 级联/实时轻聊确实无工具，保留原有声明；自定义预设也不得让助手模式带上它。 */
    @Test
    fun nonAssistantModesKeepTheNoToolsRuleAndPresetsCannotReintroduceIt() {
        val light = AdkVoiceAudioBackend.realtimeInstructions(
            preset = null, assistantMode = false, hasDeclarations = false,
        )
        assertTrue("You have no tools" in light)

        val presetAssistant = AdkVoiceAudioBackend.realtimeInstructions(
            preset = "你是一个语音助手", assistantMode = true, hasDeclarations = true,
        )
        assertFalse("你是一个语音助手" in presetAssistant)
        assertTrue("<voice_response>" in presetAssistant)
        assertFalse("<response_format>" in presetAssistant)
        assertFalse("You have no tools" in presetAssistant)
    }
}
