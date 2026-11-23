@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.modelaccess

import com.google.adk.kt.annotations.ExperimentalLiveApi
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.RealtimeInput
import com.google.adk.kt.types.AudioTranscriptionConfig
import com.google.adk.kt.types.AutomaticActivityDetection
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.LiveConnectConfig
import com.google.adk.kt.types.Modality
import com.google.adk.kt.types.RealtimeInputConfig
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.SessionResumptionConfig
import github.ponyhuang.gimi.data.modelaccess.shared.LiveConnectionContractTest
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 半级联适配器：WAV 直传、主动 VAD 约束、打断与官方 LiveConnection 契约。 */
class SemiCascadedVoiceModelTest : LiveConnectionContractTest() {
    private val llm = mockk<Model> {
        every { name } returns "fake-omni"
        every { generateContent(any(), true) } answers {
            val request = firstArg<LlmRequest>()
            val text = request.contents.last().parts.mapNotNull { it.text }.joinToString("")
            flowOf(LlmResponse(content = Content.fromText(Role.MODEL, "$text。"), partial = true))
        }
    }
    private val synthesis = mockk<SpeechSynthesisRepository> {
        every { openSession(any(), any()) } returns null
        every { synthesize(any(), any(), any()) } returns flowOf(byteArrayOf(1, 0))
    }
    private val model = SemiCascadedVoiceModel(llm, synthesis, synthesisModel = null)
    private val manual = LlmRequest(
        liveConnectConfig = LiveConnectConfig(
            responseModalities = listOf(Modality.AUDIO),
            outputAudioTranscription = AudioTranscriptionConfig(),
            realtimeInputConfig = RealtimeInputConfig(
                automaticActivityDetection = AutomaticActivityDetection(disabled = true),
            ),
        ),
    )

    override suspend fun openConnection() = model.connect(manual)

    @Test
    fun unsupportedLiveFeaturesFailBeforeAnyPaidRequest() = runTest {
        val unsupported = manual.copy(
            liveConnectConfig = manual.liveConnectConfig.copy(sessionResumption = SessionResumptionConfig()),
        )
        try {
            model.connect(unsupported)
            fail("Expected unsupported session resumption to be rejected")
        } catch (_: IllegalArgumentException) {
        }
        val connection = model.connect(manual)
        connection.sendRealtime(RealtimeInput.ActivityStart)
        try {
            connection.sendRealtime(RealtimeInput.Audio(
                Blob(mimeType = "audio/pcm;rate=48000", data = byteArrayOf(1, 0)),
            ))
            fail("Expected 16 kHz PCM16 input")
        } catch (_: IllegalArgumentException) {
        }
        connection.closeSession()
        verify(exactly = 0) { llm.generateContent(any(), any()) }
    }

    @Test
    fun manualAudioIsWrappedAsWavForTheOmniModel() = runTest {
        val connection = model.connect(manual)
        connection.sendRealtime(RealtimeInput.ActivityStart)
        connection.sendRealtime(RealtimeInput.Audio(
            Blob(mimeType = "audio/pcm;rate=16000", data = byteArrayOf(1, 0, 2, 0)),
        ))
        connection.sendRealtime(RealtimeInput.ActivityEnd)

        val responses = connection.receive().toList()
        assertTrue(responses.last().turnComplete == true)
        verify {
            llm.generateContent(match { request ->
                val blob = request.contents.last().parts.first { it.inlineData != null }.inlineData!!
                val wav = checkNotNull(blob.data)
                blob.mimeType == "audio/wav" && wav.size > 44 &&
                    String(wav.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
                    request.config.responseModalities == null
            }, true)
        }
        connection.closeSession()
    }

    @Test
    fun aNewTextTurnInterruptsTheActiveSynthesisAndIsReceivedNext() = runTest {
        val started = CompletableDeferred<Unit>()
        every { synthesis.synthesize("旧轮。", null, null) } returns flow {
            started.complete(Unit)
            awaitCancellation()
        }
        every { synthesis.synthesize("新轮。", null, null) } returns flowOf(byteArrayOf(1, 0))
        val connection = model.connect(manual)
        connection.sendContent(Content.fromText(Role.USER, "旧轮"))
        val first = async { connection.receive().toList() }
        started.await()
        connection.sendContent(Content.fromText(Role.USER, "新轮"))

        assertTrue(first.await().last().interrupted == true)
        val next = connection.receive().toList()
        assertTrue(next.last().turnComplete == true)
        assertEquals("新轮。", next.mapNotNull { it.outputTranscription?.text }.joinToString(""))
        connection.closeSession()
    }

    @Test
    fun sessionSynthesisBuffersStreamingDeltasIntoSentences() = runTest {
        val sent = mutableListOf<String>()
        val fakeSession = object : github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession {
            override fun send(text: String) { sent += text }
            override suspend fun flush() {}
            override fun cancel() {}
            override val segments: Flow<github.ponyhuang.gimi.domain.voice.repository.SynthesisSegment> = flow {
                for (s in sent) emit(github.ponyhuang.gimi.domain.voice.repository.SynthesisSegment(MutableStateFlow(s), flowOf(byteArrayOf(1, 0))))
            }
            override suspend fun close() {}
        }
        every { synthesis.openSession(any(), any()) } returns fakeSession
        every { llm.generateContent(any(), true) } answers {
            flowOf(
                LlmResponse(content = Content.fromText(Role.MODEL, "你"), partial = true),
                LlmResponse(content = Content.fromText(Role.MODEL, "好"), partial = true),
                LlmResponse(content = Content.fromText(Role.MODEL, "。"), partial = true),
                LlmResponse(content = Content.fromText(Role.MODEL, "天气"), partial = true),
                LlmResponse(content = Content.fromText(Role.MODEL, "真好"), partial = true),
                LlmResponse(content = Content.fromText(Role.MODEL, "。"), partial = true),
            )
        }
        val connection = model.connect(manual)
        connection.sendContent(Content.fromText(Role.USER, "你好"))
        val responses = connection.receive().toList()
        connection.closeSession()
        assertTrue(responses.last().turnComplete == true)
        assertEquals(listOf("你好。", "天气真好。"), sent)
    }

    @Test
    fun sessionSynthesisWrapsTtsExceptionsIntoSynthesisFailure() = runTest {
        val fakeSession = object : github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisSession {
            override fun send(text: String) {}
            override suspend fun flush() {
                throw github.ponyhuang.gimi.data.modelaccess.minimax.speech.TtsSynthesisException(
                    1002, "请求过于频繁", "rate limit exceeded(RPM)",
                )
            }
            override fun cancel() {}
            override val segments: Flow<github.ponyhuang.gimi.domain.voice.repository.SynthesisSegment> = emptyFlow()
            override suspend fun close() {}
        }
        every { synthesis.openSession(any(), any()) } returns fakeSession
        every { llm.generateContent(any(), true) } answers {
            flowOf(LlmResponse(content = Content.fromText(Role.MODEL, "你好。"), partial = true))
        }
        val connection = model.connect(manual)
        connection.sendContent(Content.fromText(Role.USER, "你好"))
        try {
            connection.receive().toList()
            fail("Expected VoiceAudioBackendException")
        } catch (e: github.ponyhuang.gimi.domain.voice.repository.VoiceAudioBackendException) {
            assertEquals(github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure.Synthesis, e.failure)
        } finally {
            connection.closeSession()
        }
    }
}
