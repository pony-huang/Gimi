package github.ponyhuang.gimi.data.agent.subtasks

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.data.agent.ModelConfig
import github.ponyhuang.gimi.domain.conversation.model.SubtaskStatus
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 保留执行边界回归：真实 ADK 工作流、并发、恢复、隔离与取消，模型由 fake 替代。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SubtaskBatchExecutorTest {
    private val config = ModelConfig("service", ApiProtocol.OpenaiChatcompletions, "fixture-model", "private-key", "https://fixture.invalid")
    private val user = AgentSessionIdentity.DEFAULT_USER_ID
    private val tools = object : Toolset {
        override suspend fun getTools(readonlyContext: com.google.adk.kt.agents.ReadonlyContext?) = emptyList<com.google.adk.kt.tools.BaseTool>()
    }

    @Test
    fun limitsConcurrencyAndReplaysCompletedResultsWithoutAnotherModelCall() = runTest {
        val sessions = InMemorySessionService()
        val store = SubtaskSessionStore(sessions)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val calls = AtomicInteger()
        val executor = SubtaskBatchExecutor(sessions, store, tools, {
            fakeModel {
                calls.incrementAndGet()
                peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                delay(100)
                active.decrementAndGet()
                "done"
            }
        })
        val tasks = (1..6).map { SubtaskRequest("task-$it", "任务 $it", "analyze-$it", "material-$it") }
        val first = executor.execute("chat", user, "inv", "call", tasks, config, null)
        assertEquals(3, peak.get())
        assertEquals(6, calls.get())
        assertTrue(first.tasks.all { it.status == SubtaskStatus.COMPLETED })
        assertEquals(6, first.tasks.map { it.nodePath }.distinct().size)
        val replay = executor.execute("chat", user, "inv", "call", tasks, config, null)
        assertEquals(first.tasks.map { it.result }, replay.tasks.map { it.result })
        assertEquals(6, calls.get())
        coroutineScope {
            val one = async { executor.execute("chat", user, "inv-one", "call", tasks.take(2), config, null) }
            val two = async { executor.execute("chat", user, "inv-two", "call", tasks.take(2), config, null) }
            one.await()
            two.await()
        }
        assertEquals(3, peak.get())
        assertTrue(sessions.listSessions(AgentSessionIdentity.APP_NAME, user).sessions.isEmpty())
        store.delete("chat")
        assertTrue(store.load("chat").isEmpty())
        assertTrue(sessions.listSessions(SubtaskSessionStore.WORKFLOW_APP, user).sessions.isEmpty())
    }

    @Test
    fun eachWorkerOnlySeesItsOwnMaterialsAndAFileReadFailureDoesNotCancelPeers() = runTest {
        val sessions = InMemorySessionService()
        val store = SubtaskSessionStore(sessions)
        val seen = mutableListOf<String>()
        val executor = SubtaskBatchExecutor(sessions, store, tools, {
            fakeModel { request ->
                val text = request.contents.flatMap { it.parts }.mapNotNull { it.text }.joinToString("\n")
                seen += text
                assertFalse(text.contains("private-key"))
                if (text.contains("material-one")) {
                    assertFalse(text.contains("material-two"))
                    throw java.io.IOException("fixture failure")
                }
                assertFalse(text.contains("material-one"))
                "second result"
            }
        })
        val result = executor.execute("chat", user, "inv", "call", listOf(
            SubtaskRequest("one", "一", "analyze", "material-one"),
            SubtaskRequest("two", "二", "analyze", "material-two"),
        ), config, mapOf("private_parent_state" to "do-not-copy"))
        assertEquals(SubtaskStatus.FAILED, result.tasks[0].status)
        assertEquals(SubtaskStatus.COMPLETED, result.tasks[1].status)
        assertEquals("second result", result.tasks[1].result)
        assertTrue(seen.all { !it.contains("do-not-copy") })
    }

    @Test
    fun cancellationStopsQueuedWorkAndPersistsCancelledInsteadOfCompleted() = runTest {
        val sessions = InMemorySessionService()
        val store = SubtaskSessionStore(sessions)
        val calls = AtomicInteger()
        val executor = SubtaskBatchExecutor(sessions, store, tools, {
            fakeModel { calls.incrementAndGet(); delay(60_000); "unexpected" }
        })
        val job = launch {
            executor.execute("chat", user, "inv", "call", (1..6).map {
                SubtaskRequest("$it", "任务", "analyze", "materials")
            }, config, null)
        }
        runCurrent()
        advanceTimeBy(10)
        job.cancelAndJoin()
        assertEquals(3, calls.get())
        assertTrue(store.load("chat").single().tasks.all { it.status == SubtaskStatus.CANCELLED })
    }

    @Test
    fun timeoutIsPerTaskAndNeverReportedAsSuccess() = runTest {
        val sessions = InMemorySessionService()
        val store = SubtaskSessionStore(sessions)
        val executor = SubtaskBatchExecutor(sessions, store, tools, {
            fakeModel { delay(10_000); "late" }
        }, timeoutMillis = 20)
        val result = executor.execute("chat", user, "inv", "call",
            listOf(SubtaskRequest("one", "任务", "analyze", "")), config, null)
        assertEquals(SubtaskStatus.FAILED, result.tasks.single().status)
        assertTrue(result.tasks.single().result.isEmpty())
    }

    private fun fakeModel(answer: suspend (LlmRequest) -> String) = object : Model {
        override val name = "fixture-model"
        override fun generateContent(request: LlmRequest, stream: Boolean) = flow {
            emit(LlmResponse(content = Content(role = "model", parts = listOf(Part(text = answer(request))))))
        }
    }
}
