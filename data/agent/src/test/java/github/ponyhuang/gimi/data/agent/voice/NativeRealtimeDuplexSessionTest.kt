@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.models.LiveConnection
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.modelaccess.VoiceLiveModelFactory
import github.ponyhuang.gimi.domain.voice.repository.RealtimeFunctionCallSink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeTurnControlSink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 原生 Realtime 门面的连接生命周期边界：打断收尾、断线提交重放与重连历史刷新。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeRealtimeDuplexSessionTest {

    @Test
    fun interruptCancelsPendingToolRoundBeforeTriggeringSecondResponse() = runTest {
        val toolExecutionStarted = CompletableDeferred<Unit>()
        val toolExecutionCanceled = CompletableDeferred<Unit>()
        val tools = mockk<RealtimeVoiceToolBridge> {
            every { declarations } returns emptyList()
            coEvery { execute("c1", "get_weather", any()) } coAnswers {
                toolExecutionStarted.complete(Unit)
                try {
                    awaitCancellation()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    toolExecutionCanceled.complete(Unit)
                    throw cancelled
                }
            }
        }
        val sink = mockk<RealtimeFunctionCallSink>(relaxed = true)
        val turnControl = mockk<RealtimeTurnControlSink>(relaxed = true)
        val connection = object : LiveConnection, RealtimeFunctionCallSink by sink, RealtimeTurnControlSink by turnControl {
            override fun receive() = kotlinx.coroutines.flow.flow<LlmResponse> {
                emit(
                    LlmResponse(
                        content = Content(
                            role = Role.MODEL,
                            parts = listOf(Part(functionCall = FunctionCall(name = "get_weather", args = mapOf("city" to "杭州"), id = "c1"))),
                        ),
                    ),
                )
                awaitCancellation()
            }

            override suspend fun sendRealtime(input: com.google.adk.kt.models.RealtimeInput) = Unit
            override suspend fun sendContent(content: Content, partial: Boolean) = Unit
            override suspend fun sendHistory(history: List<Content>) = Unit
            override suspend fun closeSession() = Unit
        }
        val model = mockk<Model> {
            every { name } returns "test-realtime"
            coEvery { connect(any<LlmRequest>()) } returns connection
        }
        val models = mockk<VoiceLiveModelFactory> {
            coEvery { create(any(), any()) } returns model
        }
        val session = NativeRealtimeDuplexSession(
            models = models,
            selection = null,
            instructions = "test",
            uplinkProvider = { RealtimeUplink(sampleRateHz = 16_000, resampled = false) },
            tools = tools,
        )
        val events = mutableListOf<RealtimeVoiceEvent>()
        val collector = launch { session.events().collect(events::add) }
        try {
            val started = withTimeoutOrNull(1_000) {
                while (events.none { it is RealtimeVoiceEvent.ToolCallStarted }) kotlinx.coroutines.yield()
            } != null
            assertTrue("未收到工具调用事件: $events", started)
            assertTrue("工具轮未开始执行", toolExecutionStarted.awaitWithTimeout(1_000))
            session.interrupt()
            assertTrue("工具轮未随打断取消", toolExecutionCanceled.awaitWithTimeout(5_000))
            coVerify(exactly = 1) { turnControl.cancelResponse() }
            coVerify(exactly = 0) { sink.sendResponseTrigger() }
        } finally {
            collector.cancel()
            session.close()
        }
    }

    @Test
    fun commitTurnDuringReconnectWaitsForTheReplacementConnection() = runTest {
        val sendFailed = CompletableDeferred<Unit>()
        val committedAfterReconnect = CompletableDeferred<Unit>()
        val firstConnection = object : LiveConnection {
            override fun receive() = kotlinx.coroutines.flow.flow<LlmResponse> {
                sendFailed.await()
            }

            override suspend fun sendRealtime(input: com.google.adk.kt.models.RealtimeInput) {
                sendFailed.complete(Unit)
                throw java.io.IOException("connection closed")
            }

            override suspend fun sendContent(content: Content, partial: Boolean) = Unit
            override suspend fun sendHistory(history: List<Content>) = Unit
            override suspend fun closeSession() = Unit
        }
        val turnControl = mockk<RealtimeTurnControlSink> {
            coEvery { commitAudioTurn() } coAnswers {
                committedAfterReconnect.complete(Unit)
            }
        }
        val secondConnection = object : LiveConnection, RealtimeTurnControlSink by turnControl {
            override fun receive() = kotlinx.coroutines.flow.flow<LlmResponse> { awaitCancellation() }
            override suspend fun sendRealtime(input: com.google.adk.kt.models.RealtimeInput) = Unit
            override suspend fun sendContent(content: Content, partial: Boolean) = Unit
            override suspend fun sendHistory(history: List<Content>) = Unit
            override suspend fun closeSession() = Unit
        }
        val firstModel = mockk<Model> {
            every { name } returns "test-realtime-1"
            coEvery { connect(any<LlmRequest>()) } returns firstConnection
        }
        val secondModel = mockk<Model> {
            every { name } returns "test-realtime-2"
            coEvery { connect(any<LlmRequest>()) } returns secondConnection
        }
        val models = mockk<VoiceLiveModelFactory> {
            coEvery { create(any(), any()) } returnsMany listOf(firstModel, secondModel)
        }
        val session = NativeRealtimeDuplexSession(
            models = models,
            selection = null,
            instructions = "test",
            uplinkProvider = { RealtimeUplink(sampleRateHz = 16_000, resampled = false) },
        )
        val ready = CompletableDeferred<Unit>()
        val collector = launch {
            session.events().collect {
                if (it is RealtimeVoiceEvent.Ready && !it.reconnected) ready.complete(Unit)
            }
        }
        try {
            assertTrue("首条连接未就绪", ready.awaitWithTimeout(5_000))
            session.sendAudio(byteArrayOf(1, 0))
            assertTrue("发送失败未通知接收流", sendFailed.awaitWithTimeout(5_000))

            val commitResult = runCatching { session.commitTurn() }
            assertTrue("重连等待期提交轮次不应使通话失败: ${commitResult.exceptionOrNull()}", commitResult.isSuccess)
            assertTrue("重连后未提交轮次", committedAfterReconnect.awaitWithTimeout(5_000))
        } finally {
            collector.cancel()
            session.close()
        }
    }

    @Test
    fun reconnectRefreshesConversationHistory() = runTest {
        val disconnect = CompletableDeferred<Unit>()
        val firstHistory = listOf(Content.fromText(Role.USER, "起呼历史"))
        val refreshedHistory = firstHistory + Content.fromText(Role.MODEL, "重连前回答")
        val firstReceived = CompletableDeferred<List<Content>>()
        val secondReceived = CompletableDeferred<List<Content>>()
        val firstConnection = object : LiveConnection {
            override fun receive() = kotlinx.coroutines.flow.flow<LlmResponse> { disconnect.await() }
            override suspend fun sendRealtime(input: com.google.adk.kt.models.RealtimeInput) = Unit
            override suspend fun sendContent(content: Content, partial: Boolean) = Unit
            override suspend fun sendHistory(history: List<Content>) {
                firstReceived.complete(history)
            }
            override suspend fun closeSession() = Unit
        }
        val secondConnection = object : LiveConnection {
            override fun receive() = kotlinx.coroutines.flow.flow<LlmResponse> { awaitCancellation() }
            override suspend fun sendRealtime(input: com.google.adk.kt.models.RealtimeInput) = Unit
            override suspend fun sendContent(content: Content, partial: Boolean) = Unit
            override suspend fun sendHistory(history: List<Content>) {
                secondReceived.complete(history)
            }
            override suspend fun closeSession() = Unit
        }
        val firstModel = mockk<Model> {
            every { name } returns "test-realtime-1"
            coEvery { connect(any<LlmRequest>()) } returns firstConnection
        }
        val secondModel = mockk<Model> {
            every { name } returns "test-realtime-2"
            coEvery { connect(any<LlmRequest>()) } returns secondConnection
        }
        val models = mockk<VoiceLiveModelFactory> {
            coEvery { create(any(), any()) } returnsMany listOf(firstModel, secondModel)
        }
        val persistence = mockk<ChatbotConversationWriter.Call> {
            coEvery { resolve() } returns "conversation"
            coEvery { acquire() } returns firstHistory
            coEvery { currentHistory() } returns refreshedHistory
            coEvery { release() } returns Unit
        }
        val session = NativeRealtimeDuplexSession(
            models = models,
            selection = null,
            instructions = "test",
            uplinkProvider = { RealtimeUplink(sampleRateHz = 16_000, resampled = false) },
            persistence = persistence,
        )
        val collector = launch { session.events().collect { } }
        try {
            assertTrue("首条连接未收到起呼历史", firstReceived.awaitWithTimeout(5_000))
            disconnect.complete(Unit)
            assertTrue("重连未收到刷新后的历史", secondReceived.awaitWithTimeout(5_000))
            assertEquals(
                listOf(Role.USER to "起呼历史", Role.MODEL to "重连前回答"),
                secondReceived.getCompleted().map { it.role to it.parts.singleOrNull()?.text },
            )
        } finally {
            collector.cancel()
            session.close()
        }
    }
}

private suspend fun <T> CompletableDeferred<T>.awaitWithTimeout(timeoutMillis: Long): Boolean =
    withTimeoutOrNull(timeoutMillis) {
        await()
        true
    } == true
