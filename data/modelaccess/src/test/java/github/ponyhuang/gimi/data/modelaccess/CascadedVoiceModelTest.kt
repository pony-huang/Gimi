@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.modelaccess

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.RealtimeInput
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.domain.voice.repository.RecognitionEvent
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import github.ponyhuang.gimi.data.modelaccess.shared.LiveConnectionContractTest

/** 官方 LiveConnection 的多轮、输入验证、接收互斥和关闭/打断回归。 */
class CascadedVoiceModelTest : LiveConnectionContractTest() {
    private val recognition = mockk<SpeechRecognitionRepository>()
    private val synthesis = mockk<SpeechSynthesisRepository> {
        // 默认走一次性合成：会话式合成由专门的用例覆盖。
        every { openSession() } returns null
    }
    private val textModel = mockk<Model> {
        every { name } returns "fake"
        every { generateContent(any(), true) } answers {
            val request = firstArg<LlmRequest>()
            flowOf(LlmResponse(content = Content.fromText(Role.MODEL,
                request.contents.last().parts.mapNotNull { it.text }.joinToString("") + "。"), partial = true))
        }
    }
    private val model = CascadedVoiceModel(textModel, recognition, synthesis)
    private val manual = LlmRequest(liveConnectConfig = LiveConnectConfig(
        inputAudioTranscription = AudioTranscriptionConfig(),
        outputAudioTranscription = AudioTranscriptionConfig(),
        realtimeInputConfig = RealtimeInputConfig(automaticActivityDetection = AutomaticActivityDetection(disabled = true)),
    ))

    override suspend fun openConnection(): com.google.adk.kt.models.LiveConnection {
        every { synthesis.synthesize(any()) } returns flowOf(byteArrayOf(1, 0))
        return model.connect(manual)
    }

    @Test
    fun officialRunnerConsumesTheAdapterWithoutAnApplicationSpecificSession() = runBlocking {
        withTimeout(5_000) {
            every { synthesis.synthesize(any()) } returns flowOf(byteArrayOf(1, 0))
            val runner = com.google.adk.kt.runners.InMemoryRunner(
                agent = com.google.adk.kt.agents.LlmAgent(name = "VoiceTest", model = model),
                appName = "VoiceContractTest",
            )
            val queue = com.google.adk.kt.agents.LiveRequestQueue()
            try {
                queue.sendContent(Content.fromText(Role.USER, "测试"))
                val events = runner.runLive("voice", "contract", queue,
                    com.google.adk.kt.agents.RunConfig(live = com.google.adk.kt.agents.LiveRunConfig(
                        responseModalities = listOf(Modality.AUDIO),
                    )),
                ).takeWhile { !it.turnComplete }.toList()
                assertTrue(events.any { event -> event.content?.parts.orEmpty().any { it.inlineData != null } })
            } finally {
                queue.close()
                runner.close()
            }
        }
    }

    @Test
    fun partialTextAndMultipleReceivesUseOneConnectionWithoutDuplicateAggregation() = runTest {
        every { synthesis.synthesize(any()) } returns flowOf(byteArrayOf(1, 0))
        val connection = model.connect(manual)
        connection.sendHistory(listOf(Content(role = Role.MODEL, parts = listOf(
            Part(text = "保留"), Part(inlineData = Blob(mimeType = "audio/pcm;rate=24000", data = byteArrayOf(1, 0))),
        ))))
        connection.sendContent(Content.fromText(Role.USER, "第"), partial = true)
        connection.sendContent(Content.fromText(Role.USER, "一轮"))
        val first = connection.receive().toList()
        assertTrue(first.last().turnComplete == true)
        assertEquals("第一轮。", first.mapNotNull { it.outputTranscription?.text }.joinToString(""))
        connection.sendContent(Content.fromText(Role.USER, "第二轮"))
        assertTrue(connection.receive().toList().last().turnComplete == true)
        verify { textModel.generateContent(match { request ->
            request.contents.first().parts.size == 1 && request.contents.first().parts.single().text == "保留"
        }, true) }
        connection.closeSession()
        assertTrue(connection.receive().toList().isEmpty())
        connection.closeSession()
    }

    @Test
    fun manualAudioCopiesChunksAndEmptyRecognitionNeverRunsGenerationOrSynthesis() = runTest {
        every { recognition.transcribeStreaming(byteArrayOf(1, 2, 3, 4)) } returns flowOf(RecognitionEvent.Final(" \n "))
        val connection = model.connect(manual)
        connection.sendRealtime(RealtimeInput.ActivityStart)
        val bytes = byteArrayOf(1, 2, 3)
        connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=16000", data = bytes)))
        bytes.fill(9)
        connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=16000", data = byteArrayOf(4))))
        connection.sendRealtime(RealtimeInput.ActivityEnd)
        val responses = connection.receive().toList()
        assertEquals("", responses.first().inputTranscription?.text)
        assertTrue(responses.last().turnComplete == true)
        verify(exactly = 0) { textModel.generateContent(any(), any()) }
        verify(exactly = 0) { synthesis.synthesize(any()) }
        connection.closeSession()
    }

    @Test
    fun newTextInterruptsAnActiveSynthesisAndNextReceiveProcessesTheNewTurn() = runTest {
        val started = CompletableDeferred<Unit>()
        every { synthesis.synthesize("旧轮。") } returns flow {
            started.complete(Unit)
            awaitCancellation()
        }
        every { synthesis.synthesize("新轮。") } returns flowOf(byteArrayOf(1, 0))
        val connection = model.connect(manual)
        connection.sendContent(Content.fromText(Role.USER, "旧轮"))
        val first = async { connection.receive().toList() }
        started.await()
        connection.sendContent(Content.fromText(Role.USER, "新轮"))
        assertTrue(first.await().last().interrupted)
        val next = connection.receive().toList()
        assertTrue(next.last().turnComplete == true)
        assertEquals("新轮。", next.mapNotNull { it.outputTranscription?.text }.joinToString(""))
        connection.closeSession()
    }

    @Test
    fun unsupportedConfigAndMalformedPcmFailBeforePaidRequests() = runTest {
        try {
            model.connect(LlmRequest(liveConnectConfig = LiveConnectConfig(sessionResumption = SessionResumptionConfig())))
            fail("Expected unsupported resumption to fail")
        } catch (_: IllegalArgumentException) { }
        val connection = model.connect(manual)
        connection.sendRealtime(RealtimeInput.ActivityStart)
        try {
            connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=48000", data = byteArrayOf(1, 0))))
            fail("Expected incompatible input rate to fail")
        } catch (_: IllegalArgumentException) { }
        connection.sendRealtime(RealtimeInput.Audio(Blob(mimeType = "audio/pcm;rate=16000", data = byteArrayOf(1))))
        try {
            connection.sendRealtime(RealtimeInput.ActivityEnd)
            fail("Expected incomplete PCM16 to fail")
        } catch (_: IllegalArgumentException) { }
        verify(exactly = 0) { recognition.transcribeStreaming(any()) }
        verify(exactly = 0) { synthesis.synthesize(any()) }
        connection.closeSession()
    }

    @Test
    fun synthesisCancellationIsNotConvertedIntoABusinessFailure() = runTest {
        every { synthesis.synthesize(any()) } returns flow { throw CancellationException("cancelled synthesis") }
        val connection = model.connect(manual)
        connection.sendContent(Content.fromText(Role.USER, "取消"))
        try {
            connection.receive().toList()
            fail("Expected synthesis cancellation")
        } catch (_: CancellationException) {
            // 独立取消不等于合成失败，不能弹错误或自动重试付费请求。
        } finally {
            connection.closeSession()
        }
    }

    @Test
    fun closingAnActiveConnectionCancelsItsNetworkProducer() = runTest {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        every { synthesis.synthesize(any()) } returns flow {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                stopped.complete(Unit)
            }
        }
        val connection = model.connect(manual)
        connection.sendContent(Content.fromText(Role.USER, "关闭"))
        val receiving = async { connection.receive().toList() }
        started.await()
        connection.closeSession()
        stopped.await()
        receiving.await()
        connection.closeSession()
    }
}
