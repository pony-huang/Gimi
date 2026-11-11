package github.ponyhuang.gimi.data.agent.phone

import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneController
import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneResult
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 直控子代理边界：跨 invocation 的任务提取、结构化失败回传与执行权释放。 */
class DirectPhoneAgentTest {
    private val controller = mockk<DirectPhoneController>(relaxed = true)

    @Test
    fun delegatesUserTextAndEmitsPairedResult() = runTest {
        coEvery { controller.execute(any(), any(), any()) } returns
            DirectPhoneResult(DirectPhoneResult.STATUS_COMPLETED, "done")
        val agent = DirectPhoneAgent(controller)

        val events = agent.runAsync(
            context(agent, transferEvent(), userEvent("Open Alipay and scan the code")),
        ).toList()

        coVerify(exactly = 1) { controller.execute(any(), "chat-1", "Open Alipay and scan the code") }
        assertEquals(2, events.size)
        val call = events[0].functionCalls().single()
        val response = events[1].functionResponses().single()
        assertEquals(DirectPhoneAgent.RESULT_TOOL, call.name)
        assertEquals(DirectPhoneAgent.RESULT_TOOL, response.name)
        assertEquals(call.id, response.id)
        assertEquals(DirectPhoneResult.STATUS_COMPLETED, response.response["status"])
        assertEquals("done", response.response["message"])
        // 成对的调用与结果不能是终局回答，否则父模型循环被直接结束。
        assertFalse(events.last().isFinalResponse)
        coVerify(exactly = 1) { controller.finishExecution(any()) }
    }

    @Test
    fun ownerIsChannelScopedAndUniquePerDelegation() = runTest {
        val owners = mutableListOf<String>()
        coEvery { controller.execute(capture(owners), any(), any()) } returns
            DirectPhoneResult(DirectPhoneResult.STATUS_COMPLETED, "done")
        val metadata = ToolRunMetadata.of(
            ModelRuntimeMetadata("service", ApiProtocol.OpenaiChatcompletions, "vision", "https://example.com", true),
            null, true, mobileUseOwner = "turn",
        )
        val agent = DirectPhoneAgent(controller)

        agent.runAsync(
            context(agent, userEvent("do it"), runConfig = RunConfig(customMetadata = metadata)),
        ).toList()
        agent.runAsync(
            context(agent, userEvent("do it"), runConfig = RunConfig(customMetadata = metadata)),
        ).toList()

        assertEquals(2, owners.size)
        owners.forEach { owner ->
            assertTrue(owner.startsWith("turn/${DirectPhoneAgent.OWNER_SCOPE}/"))
        }
        // 每次委派独立执行权；释放的 owner 必须与交给后端的完全一致。
        assertNotEquals(owners[0], owners[1])
        coVerify(exactly = 1) { controller.finishExecution(owners[0]) }
        coVerify(exactly = 1) { controller.finishExecution(owners[1]) }
    }

    @Test
    fun prefersMostRecentUserTextEvenWhenLastUserEventHasNoText() = runTest {
        coEvery { controller.execute(any(), any(), any()) } returns
            DirectPhoneResult(DirectPhoneResult.STATUS_COMPLETED, "done")
        val agent = DirectPhoneAgent(controller)

        agent.runAsync(
            context(
                agent,
                userEvent("Old task", invocationId = "inv-0"),
                userEvent("Open Alipay and scan the code"),
                functionResponseEvent(),
            ),
        ).toList()

        coVerify(exactly = 1) { controller.execute(any(), "chat-1", "Open Alipay and scan the code") }
    }

    @Test
    fun resumedInvocationRecoversTaskTextFromEarlierInvocation() = runTest {
        coEvery { controller.execute(any(), any(), any()) } returns
            DirectPhoneResult(DirectPhoneResult.STATUS_COMPLETED, "done")
        val agent = DirectPhoneAgent(controller)

        val events = agent.runAsync(
            context(agent, userEvent("Open Alipay and scan the code", invocationId = "inv-0"), functionResponseEvent()),
        ).toList()

        coVerify(exactly = 1) { controller.execute(any(), "chat-1", "Open Alipay and scan the code") }
        assertEquals(
            DirectPhoneResult.STATUS_COMPLETED,
            events.last().functionResponses().single().response["status"],
        )
    }

    @Test
    fun emptyTaskRejectsWithoutBackendCall() = runTest {
        val agent = DirectPhoneAgent(controller)

        val events = agent.runAsync(context(agent, transferEvent())).toList()

        assertEquals(
            DirectPhoneResult.STATUS_EMPTY_TASK,
            events.last().functionResponses().single().response["status"],
        )
        coVerify(exactly = 0) { controller.execute(any(), any(), any()) }
        coVerify(exactly = 0) { controller.finishExecution(any()) }
    }

    @Test
    fun missingSessionIdEmitsUnavailableWithoutBackendCall() = runTest {
        val agent = DirectPhoneAgent(controller)

        val events = agent.runAsync(context(agent, userEvent("do it"), sessionId = null)).toList()

        assertEquals(
            DirectPhoneResult.STATUS_UNAVAILABLE,
            events.last().functionResponses().single().response["status"],
        )
        coVerify(exactly = 0) { controller.execute(any(), any(), any()) }
        coVerify(exactly = 0) { controller.finishExecution(any()) }
    }

    @Test
    fun backendFailureEmitsStructuredResultWithoutAbortingTurn() = runTest {
        coEvery { controller.execute(any(), any(), any()) } throws IllegalStateException("backend broke")
        val agent = DirectPhoneAgent(controller)

        val events = agent.runAsync(context(agent, userEvent("do it"))).toList()

        assertEquals(2, events.size)
        val response = events.last().functionResponses().single()
        assertEquals(DirectPhoneResult.STATUS_FAILED, response.response["status"])
        assertTrue(response.response["message"].toString().isNotBlank())
        assertFalse(events.last().isFinalResponse)
        coVerify(exactly = 1) { controller.finishExecution(any()) }
    }

    @Test
    fun releaseFailureDoesNotOverrideStructuredResult() = runTest {
        coEvery { controller.execute(any(), any(), any()) } returns
            DirectPhoneResult(DirectPhoneResult.STATUS_COMPLETED, "done")
        coEvery { controller.finishExecution(any()) } throws IllegalStateException("release broke")
        val agent = DirectPhoneAgent(controller)

        val events = agent.runAsync(context(agent, userEvent("do it"))).toList()

        assertEquals(2, events.size)
        assertEquals(
            DirectPhoneResult.STATUS_COMPLETED,
            events.last().functionResponses().single().response["status"],
        )
    }

    @Test
    fun cancellationStillReleasesExecution() = runBlocking {
        coEvery { controller.execute(any(), any(), any()) } throws CancellationException("user stopped")
        val agent = DirectPhoneAgent(controller)
        val events = mutableListOf<Event>()

        try {
            agent.runAsync(context(agent, userEvent("do it"))).collect { events += it }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(events.isEmpty())
        }
        coVerify(exactly = 1) { controller.finishExecution(any()) }
        Unit
    }

    private fun context(
        agent: DirectPhoneAgent,
        vararg events: Event,
        runConfig: RunConfig = RunConfig(),
        sessionId: String? = "chat-1",
    ) = InvocationContext(
        agent = agent,
        session = Session(SessionKey("test", "user", sessionId), events = events.toMutableList()),
        runConfig = runConfig,
        invocationId = INVOCATION_ID,
    )

    private fun userEvent(text: String, invocationId: String = INVOCATION_ID) = Event(
        invocationId = invocationId,
        author = "user",
        content = Content(parts = listOf(Part(text = text))),
    )

    /** 恢复型 invocation 的用户事件：只有 FunctionResponse，没有可提取的文本。 */
    private fun functionResponseEvent(invocationId: String = INVOCATION_ID) = Event(
        invocationId = invocationId,
        author = "user",
        content = Content(parts = listOf(Part(functionResponse = FunctionResponse(
            id = "resume-1",
            name = "adk_request_confirmation",
            response = mapOf("confirmed" to true),
        )))),
    )

    private fun transferEvent() = Event(
        invocationId = INVOCATION_ID,
        author = "assistant",
        content = Content(parts = listOf(Part(functionCall = FunctionCall(
            id = "transfer-1",
            name = "transfer_to_agent",
            args = mapOf("agent_name" to DirectPhoneAgent.NAME),
        )))),
    )

    private companion object {
        const val INVOCATION_ID = "inv-1"
    }
}
